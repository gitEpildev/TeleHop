"""Exercise the packaged plugins with a real client, two Paper servers and one Velocity proxy."""
import argparse
from contextlib import ExitStack
import hashlib
import math
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import time
import zipfile
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from server_smoke import SERVERS, mysql, filename as distribution_name
from runtime_download import download

PLUGINS = {
    "LuckPerms.jar": ("https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar",
                      "49cecb66fa1fd22a133039a490e9c1e5095a238e7cd66eb9d2a16fe6c897550d"),
    "Multiverse-Core.jar": ("https://cdn.modrinth.com/data/3wmN97b8/versions/de7TbcZG/multiverse-core-5.8.2.jar",
                            "792252b6bbcf17c94ab8385e595281c830d7a40c946aea1832d8a010702bb630"),
}


class Process:
    def __init__(self, stack, directory, args, stop):
        self.log = directory / "acceptance.log"
        output = stack.enter_context(self.log.open("w"))
        self.proc = subprocess.Popen(args, cwd=directory, stdin=subprocess.PIPE, stdout=output,
                                     stderr=subprocess.STDOUT, text=True)
        self.stop = stop
        stack.callback(self.close)

    def mark(self):
        return len(self.log.read_text(errors="replace"))

    def send(self, text):
        self.proc.stdin.write(text + "\n")
        self.proc.stdin.flush()

    def wait(self, pattern, start=0, seconds=75):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            log = re.sub(r"\x1b\[[0-9;]*m", "", self.log.read_text(errors="replace")[start:])
            match = re.search(pattern, log)
            if match:
                return match
            if self.proc.poll() is not None:
                break
            time.sleep(0.2)
        raise AssertionError(f"{self.log}: expected {pattern!r}\n{log[-2200:]}")

    def command(self, text, expect):
        if text.startswith(("/spawn", "/hub", "/gv")):
            time.sleep(1.1)  # Exercise distinct commands outside the player debounce window.
        mark = self.mark()
        self.send(text)
        return self.wait(expect, mark)

    def close(self):
        if self.proc.poll() is None:
            try:
                if self.stop:
                    self.send(self.stop)
                    self.proc.wait(timeout=45)
                else:
                    self.proc.terminate()
                    self.proc.wait(timeout=5)
            except (subprocess.TimeoutExpired, BrokenPipeError):
                self.proc.kill()
                self.proc.wait()


def assert_entity_values(server, field, expected, player="ReviewMember"):
    for _ in range(20):
        if entity_values_match(server, field, expected, player):
            return
        time.sleep(0.1)
    raise AssertionError(f"{player} {field} did not reach {expected}")


def entity_vector(server, field, player):
    match = server.command(f"data get entity {player} {field}", r"entity data: \[([^\]]+)\]")
    return [float(value.strip().rstrip("df")) for value in match[1].split(",")]


def entity_values_match(server, field, expected, player="ReviewMember"):
    actual = entity_vector(server, field, player)
    delta = [a - b for a, b in zip(actual, expected, strict=True)]
    if field == "Rotation":
        delta[0] = math.remainder(delta[0], 360)
    return all(abs(value) <= 0.01 for value in delta)


def regional_settings(radius, dimension="meadow"):
    return (f"rtp:\n  max-radius: {radius}\n  cooldown-seconds: 0\n  delay-seconds: 0\n"
            f"  default-dimension: {dimension}\n"
            f"  dimensions: {{{dimension}: world, depths: world_nether, sky: world_the_end}}\n"
            "  gui: {dimension-items: {depths: {material: NETHERRACK}, sky: {material: END_STONE}}}\n"
            f"  regions:\n    mines: {{radius: {radius}, gui: {{material: RED_CONCRETE}}}}\n"
            f"    port: {{radius: {radius}, gui: {{material: BLUE_CONCRETE}}}}\n")


def configure_variants(plugin):
    root = plugin / "commands"
    root.mkdir(exist_ok=True)
    (root / "rtp.yml").write_text("""rtp:
  additional-commands:
    enabled: true
    commands:
      mines-trip:
        enabled: true
        name: minestrip
        aliases: [minejump]
        destination: {region: mines, dimension: meadow}
        gui: {enabled: false}
        settings: {inherit-defaults: true, overrides: {cooldown-seconds: 0, delay-seconds: 0, max-radius: 8}}
        permissions: {require-command-permission: true}
""")
    (root / "spawn.yml").write_text("""spawn:
  additional-commands:
    enabled: true
    commands:
      garden-trip:
        enabled: true
        name: gardenvisit
        aliases: [gv]
        destination: {type: NAMED_SPAWN, target: gardens}
        permissions: {require-command-permission: true}
""")


def paper(stack, args, name, port, root, *, general=None, spawns=None, storage=None, rtp=None,
          features=None, hub_command=None, variants=False, profile="single", regions=None, messaging=None,
          folder_name=None, plugin_jar=None, extra_plugins=PLUGINS):
    folder = root / (folder_name or name)
    folder.mkdir()
    template = args.paper
    # Older servers save dimensions beside the primary world. Newer releases keep them inside it.
    worlds = sorted(record.parent for record in template.glob("*/level.dat"))
    if not worlds:
        raise FileNotFoundError(f"No saved worlds in the smoke server at {template}")
    for world in worlds:
        shutil.copytree(world, folder / world.name)
    for shared in ("server.jar", "cache", "libraries", "versions"):
        (folder / shared).symlink_to((template / shared).resolve())
    shutil.copytree(template / "config", folder / "config")
    for filename in ("eula.txt", "bukkit.yml", "spigot.yml"):
        shutil.copy(template / filename, folder)
    (folder / "server.properties").write_text(
        f"server-port={port}\nserver-ip=127.0.0.1\nonline-mode=false\nenforce-secure-profile=false\n"
        "view-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-seed=245\n")
    config = folder / "plugins/TeleHop/config"
    config.mkdir(parents=True)
    (config / "database.yml").write_text(
        f"mysql:\n  host: 127.0.0.1\n  port: {os.environ['MYSQL_PORT']}\n  database: telehop_ci\n"
        "  username: telehop\n  password: ci-password\n  pool-size: 2\n")
    # The rollback JAR reads the original split layout. V4 fixtures write the selected profile directly.
    if plugin_jar:
        config_root = config
    else:
        config_root = config / profile
        config_root.mkdir()
        flags = ("commands:\n  custom-names: {enabled: true}\n  additional-commands: {enabled: true}\n"
                 if variants else "")
        (config.parent / "config.yml").write_text(f"config-version: 1\nmode: {profile.upper()}\n" + flags)
    (config_root / "general.yml").write_text(general or f"server-name: {name}\nhub-server: hub\n")
    for filename, content in (("features.yml", features), ("spawns.yml", spawns), ("rtp.yml", rtp),
                              ("messaging.yml", messaging), ("regions.yml", regions)):
        if content is not None:
            (config_root / filename).write_text(content)
    if variants:
        configure_variants(config.parent)
    if hub_command:
        folder_commands = config.parent / "commands"
        folder_commands.mkdir(exist_ok=True)
        (folder_commands / "hub.yml").write_text(hub_command)
    if storage:
        (config.parent / "storage.yml").write_text(storage)
    shutil.copy(plugin_jar or args.bundle / distribution_name("paper", args.version), folder / "plugins")
    for filename in extra_plugins:
        shutil.copy(root / filename, folder / "plugins")
    return Process(stack, folder, ["java", "-Xms256m", "-Xmx1000m", "-XX:ActiveProcessorCount=2",
                                  "-jar", "server.jar", "--nogui"], "stop")


def proxy(stack, args, root, ports, *, folder_name="proxy", settings="", plugin_jar=None, server_names=None):
    folder = root / folder_name
    folder.mkdir()
    name, digest = SERVERS[args.proxy]
    download(folder / "server.jar", f"https://fill-data.papermc.io/v1/objects/{digest}/{name}", digest)
    with zipfile.ZipFile(folder / "server.jar") as jar:
        toml = jar.read("default-velocity.toml").decode()
    toml = re.sub(r'^bind = .*', f'bind = "127.0.0.1:{ports[0]}"', toml, flags=re.M)
    toml = toml.replace("online-mode = true", "online-mode = false")
    # All test clients connect from the same loopback address.
    toml = re.sub(r'^login-ratelimit = .*', 'login-ratelimit = 0', toml, flags=re.M)
    toml = re.sub(r'^player-info-forwarding-mode = .*', 'player-info-forwarding-mode = "NONE"', toml, flags=re.M)
    names = server_names or ("hub", "survival")[:len(ports) - 1]
    backends = "[servers]\n" + "".join(f'{name} = "127.0.0.1:{port}"\n'
                                         for name, port in zip(names, ports[1:], strict=True))
    toml = re.sub(r'\[servers\].*?\[forced-hosts\]', backends + f'try = ["{names[0]}"]\n\n[forced-hosts]', toml, flags=re.S)
    toml = re.sub(r'\[forced-hosts\].*?(?=\n\[)', '[forced-hosts]\n', toml, flags=re.S)
    (folder / "velocity.toml").write_text(toml)
    (folder / "forwarding.secret").write_text(secrets.token_hex(32))
    config = folder / "plugins/telehop-velocity"
    config.mkdir(parents=True)
    if not settings:
        settings = f"servers.hub={names[0]}\nservers.backends=" + ",".join(names) + "\n"
    (config / "config.properties").write_text(
        f"mysql.host=127.0.0.1\nmysql.port={os.environ['MYSQL_PORT']}\nmysql.database=telehop_ci\n"
        "mysql.username=telehop\nmysql.password=ci-password\n" + settings)
    shutil.copy(plugin_jar or args.bundle / f"telehop-velocity-{args.version}.jar", folder / "plugins")
    return Process(stack, folder, [args.proxy_java, "-Xmx384m", "-jar", "server.jar"], "shutdown")


def delegated_staff(stack, root, port, client, survival):
    guest_dir = root / "guest"
    guest_dir.mkdir()
    guest = Process(stack, guest_dir, ["node", str(Path(__file__).with_name("client.cjs").resolve()),
                                       str(port), "ReviewGuest"], None)
    guest.wait("CLIENT_READY")
    mark = survival.mark()
    guest.send("/server survival")
    survival.wait("ReviewGuest joined the game", mark)
    # Prepare level ground before either client moves. Both clients simulate gravity between packets.
    survival.command("minecraft:forceload add 33 10", r"(?i)marked chunk|already")
    survival.command("minecraft:tp ReviewGuest 33.5 88 10.5 24 13", "Teleported")
    survival.command("minecraft:setblock 33 87 10 minecraft:stone", r"(?i)changed|already|could not set")
    survival.command("minecraft:setblock 41 81 14 minecraft:stone", r"(?i)changed|already|could not set")
    survival.command("gamemode creative ReviewGuest", r"(?i)creative")
    survival.command("minecraft:tp ReviewGuest 33.5 88 10.5 24 13", "Teleported")
    guest.command("/sethome review", "Home review set")
    survival.command("minecraft:tp ReviewGuest 41.5 82 14.5 8 3", "Teleported")
    survival.command("gamemode creative ReviewMember", r"(?i)creative|already")
    survival.command("deop ReviewMember", r"operator|opped")
    survival.command("lp user ReviewMember permission set telehop.tp.player.self true", "true")
    mark = survival.mark()
    client.send("/tp ReviewGuest")
    survival.wait("issued server command: /tp ReviewGuest", mark)
    assert_entity_values(survival, "Pos", [41.5, 82, 14.5])
    client.command("/tp ReviewGuest ReviewMember", r"(?i)permission")

    # Offline UUIDs are deterministic on this isolated proxy fixture.
    owner = uuid.UUID(bytes=hashlib.md5(b"OfflinePlayer:ReviewGuest", usedforsecurity=False).digest(), version=3)
    client.command("/listhomes ReviewGuest", "ReviewGuest's Homes")
    client.command(f"/listhomes visit {owner} review", r"(?i)permission|unknown.*command|no access")
    survival.command("lp user ReviewMember permission set telehop.admin.homes.visit true", "true")
    mark = survival.mark()
    client.send(f"/listhomes visit {owner} review")
    survival.wait(f"issued server command: /listhomes visit {owner} review", mark)
    time.sleep(0.5)
    assert_entity_values(survival, "Pos", [33.5, 88, 10.5])
    client.command(f"/forcedelhome-confirm {owner} review confirm", r"(?i)permission|unknown.*command|no access")
    survival.command("lp user ReviewMember permission set telehop.admin.homes.delete true", "true")
    client.command(f"/forcedelhome-confirm {owner} review", "Click to confirm")
    assert mysql(f"SELECT name FROM homes WHERE uuid='{owner}' AND name='review'") == ["review"]
    client.command(f"/forcedelhome-confirm {owner} review confirm", "Force-deleted home")
    assert not mysql(f"SELECT name FROM homes WHERE uuid='{owner}' AND name='review'")


def menu_travel(root, client, hub, survival):
    # Select actual inventory items and inspect the player's position on the destination backend.
    def return_to_hub():
        mark = hub.mark()
        client.send("/server hub")
        hub.wait("ReviewMember joined the game", mark)

    return_to_hub()
    bed = client.command("/home", r"MENU [^\n]*?([0-9]+)=lime_bed")
    manage = client.command(f":click {bed[1]} lime_bed", r"MENU [^\n]*?([0-9]+)=lime_wool")
    mark = survival.mark()
    client.send(f":click {manage[1]} lime_wool")
    survival.wait("ReviewMember joined the game", mark)
    assert_entity_values(survival, "Pos", [21.5, 96, -11.5])

    return_to_hub()
    general = root / "hub/plugins/TeleHop/config/single/general.yml"
    general.write_text("server-name: hub\nhub-server: hub\nservers: {port: hub, mines: survival}\n")
    destination_rtp = root / "survival/plugins/TeleHop/config/single/rtp.yml"
    destination_rtp.write_text(regional_settings(32))
    survival.command("telehop reload", r"(?i)reloaded")
    survival.command("minecraft:forceload add -32 -32 32 32", r"(?i)marked .*chunk|already")
    survival.command("minecraft:fill -32 200 -32 32 200 32 minecraft:stone", r"(?i)filled|blocks")
    client.command("/telehop reload", r"(?i)reloaded")
    regional_rtp(client, "ReviewMember", survival, radius=32, landing_y=201)
    # Source and destination use independent LuckPerms stores and evaluate the same stable variant ID.
    return_to_hub()
    hub.command("lp user ReviewMember permission set telehop.command.mines-trip false", "false")
    client.command("/minejump", r"(?i)permission")
    hub.command("lp user ReviewMember permission set telehop.command.mines-trip true", "true")
    survival.command("lp user ReviewMember permission set telehop.command.mines-trip false", "false")
    mark = survival.mark()
    client.command("/minejump", r"(?i)destination is unavailable|permission")
    survival.wait("ReviewMember joined the game", mark)
    return_to_hub()
    survival.command("lp user ReviewMember permission set telehop.command.mines-trip true", "true")
    mark = survival.mark()
    client.send("/minestrip")
    survival.wait("ReviewMember joined the game", mark)
    for _ in range(40):
        x, y, z = entity_vector(survival, "Pos", "ReviewMember")
        if -8 <= x < 9 and -8 <= z < 9 and abs(y - 201) < .01:
            break
        time.sleep(.2)
    else:
        raise AssertionError(f"Command variant missed its configured platform and radius: {(x, y, z)}")
    # Return to a different server and use the named-spawn alias with its permission granted at each end.
    return_to_hub()
    for backend in (hub, survival):
        backend.command("lp user ReviewMember permission set telehop.command.garden-trip true", "true")
    client.command("/gv", r"Arrived at gardens")
    assert_entity_values(survival, "Pos", [21.5, 96, -11.5])

    # A global grant in training must yield to a denial scoped to the destination world.
    survival.command("mv tp ReviewMember training", r"(?i)teleport")
    survival.command("data get entity ReviewMember Dimension", "training")
    survival.command("lp user ReviewMember permission set telehop.command.garden-trip false world=world", "false")
    client.command("/gv", r"(?i)permission")
    survival.command("data get entity ReviewMember Dimension", "training")
    survival.command("lp user ReviewMember permission set telehop.command.garden-trip true world=world", "true")
    client.command("/gv", r"Arrived at gardens")
    survival.command("data get entity ReviewMember Dimension", r"minecraft:overworld")
    assert_entity_values(survival, "Pos", [21.5, 96, -11.5])



def regional_rtp(client, player, survival, *, radius, landing_y):
    region = client.command("/rtp", r"MENU [^\n]*?([0-9]+)=red_concrete")
    dimension = client.command(f":click {region[1]} red_concrete", r"MENU [^\n]*?([0-9]+)=grass_block")
    mark = survival.mark()
    client.command(f":click {dimension[1]} grass_block", r"CHAT .*Teleporting\.\.\.")
    survival.wait(re.escape(player) + " joined the game", mark)
    survival.command(f"data get entity {player} Dimension", r"minecraft:overworld")
    # Paper acknowledges completion asynchronously after reporting the RTP search result.
    for _ in range(30):
        x, y, z = entity_vector(survival, "Pos", player)
        if -radius <= x < radius + 1 and -radius <= z < radius + 1 and abs(y - landing_y) < 0.01:
            return
        time.sleep(0.2)
    raise AssertionError(f"{player} landed outside the selected RTP destination: {(x, y, z)}")


def run(args):
    root = args.directory.resolve()
    root.mkdir(parents=True, exist_ok=False)
    for name, (url, digest) in PLUGINS.items():
        download(root / name, url, digest)
    with ExitStack() as reservations:
        sockets = [reservations.enter_context(socket.socket()) for _ in range(3)]
        for sock in sockets:
            sock.bind(("127.0.0.1", 0))
        ports = [sock.getsockname()[1] for sock in sockets]
    with ExitStack() as stack:
        features = "features: {spawn: true, hub: true}\n"
        routes = ("routing:\n  enabled: true\n  default: {server: hub}\n"
                  "  worlds: {training: {server: survival, spawn: gardens}}\n")
        topology = "hub-server: hub\nservers: {port: hub, mines: survival}\n"
        hub = paper(stack, args, "hub", ports[1], root, features=features, spawns="destinations: {}\n",
                    general="server-name: hub\n" + topology, rtp=regional_settings(32), variants=True)
        survival = paper(stack, args, "survival", ports[2], root, features=features, general="server-name: survival\n" + topology,
                         hub_command="hub: {custom-name: {enabled: true, name: lobby, aliases: [hub]}}\n", spawns=routes, rtp=regional_settings(32), variants=True)
        bridge = proxy(stack, args, root, ports)
        for server in (hub, survival, bridge):
            server.wait(r"Done \(", seconds=180)
        # First generation can block the game thread long enough to disconnect an idle client.
        survival.command("mv create training normal", r"(?i)world 'training' created")
        client_dir = root / "client"
        client_dir.mkdir()
        client = Process(stack, client_dir, ["node", str(Path(__file__).with_name("client.cjs").resolve()), str(ports[0])], None)
        client.wait("CLIENT_READY", seconds=150)
        hub.command("op ReviewMember", r"operator|opped")
        client.command("/telehop spawn setdefault", "Saved the default spawn")
        client.command("/spawn", "Teleporting to spawn")
        assert mysql("SELECT default_world, routing_enabled, hub_command FROM spawn_backends WHERE server='hub'") == ["world\t0\t1"]
        # Registration and the shared directory must still describe the running Paper instance after a reload.
        switches = root / "hub/plugins/TeleHop/config/single/features.yml"
        original_switches = switches.read_text()
        switches.write_text("features: {spawn: true, hub: false}\n")
        client.command("/telehop reload", "Restart Paper to apply the hub command setting")
        assert mysql("SELECT hub_command FROM spawn_backends WHERE server='hub'") == ["1"]
        client.command("/telehop help", "/hub takes you to the global hub")
        switches.write_text(original_switches)
        client.command("/telehop reload", r"(?i)reloaded")
        connected = survival.mark()
        client.send("/server survival")
        survival.wait("ReviewMember joined the game", connected)
        survival.command("op ReviewMember", r"operator|opped")
        survival.command("gamemode spectator ReviewMember", r"[Ss]pectator")
        survival.command("minecraft:tp ReviewMember 21.5 96 -11.5 33 -12", "Teleported")
        survival.command("minecraft:setblock 21 95 -12 minecraft:stone", r"(?i)changed|already|could not set")
        client.command("/telehop spawn set gardens public", "Saved gardens")
        client.command("/sethome review", "Home review set")
        client.command("/setwarp review", "Warp review created")
        survival.command("mv tp ReviewMember training", r"(?i)teleport")
        time.sleep(1)
        client.command("/telehop spawn set staff-room restricted", "Saved staff-room")
        # This name is created exclusively by editing YAML while the servers are running.
        chosen = "orchard-" + secrets.token_hex(3)
        settings = root / "survival/plugins/TeleHop/config/single/spawns.yml"
        original = settings.read_text()
        marker = "\ndestinations:\n"
        assert marker in original
        edited = original.replace(marker, marker + f"  {chosen}: {{world: world, x: 21.5, y: 96, z: -11.5, "
                                  "yaw: 33, pitch: -12, access: public}\n", 1)
        settings.write_text(edited)
        client.command("/telehop reload", r"(?i)reloaded")
        client.command("/spawn " + chosen, "Arrived at " + chosen)
        rows = mysql("SELECT server,world,x,y,z,yaw,pitch,restricted FROM named_spawns WHERE id='" + chosen + "'")
        assert rows == ["survival\tworld\t21.5\t96\t-11.5\t33\t-12\t0"], rows
        assert mysql("SELECT target_spawn FROM spawn_routes WHERE server='survival' AND source_world='training'") == ["gardens"]
        settings.write_text(edited.replace("pitch: -12, access: public", "pitch: invalid, access: public", 1))
        client.command("/telehop reload", r"Could not reload")
        client.command("/spawn " + chosen, "Arrived at " + chosen)
        settings.write_text(original)
        client.command("/telehop reload", r"(?i)reloaded")
        client.command("/spawn " + chosen, "destination is unavailable")
        assert not mysql("SELECT id FROM named_spawns WHERE id='" + chosen + "'")
        survival.command("deop ReviewMember", r"operator|opped")
        client.command("/spawn staff-room", r"(?i)permission")
        client.command("/listhomes ReviewMember", r"(?i)permission|not allowed|unknown.*command")
        survival.command("lp user ReviewMember permission set telehop.admin.homes.list true", "true")
        client.command("/listhomes ReviewMember", r"ReviewMember's Homes")
        client.command("/tp ReviewMember ReviewMember", r"(?i)permission")
        client.command("/home review", r"Teleporting to review")
        client.command("/warp review", r"Teleporting to warp review")
        client.command("/tpatoggle", r"TPA requests disabled")
        client.command("/tpatoggle", r"TPA requests enabled")
        survival.command("lp user ReviewMember permission set telehop.spawn.destination.staff-room true", "true")
        client.command("/spawn gardens", "Arrived at gardens")
        assert_entity_values(survival, "Pos", [21.5, 96, -11.5])
        assert_entity_values(survival, "Rotation", [33, -12])
        client.command("/spawn staff-room", "Arrived at staff-room")
        survival.command("data get entity ReviewMember Dimension", r"training")
        client.command("/spawn", "Arrived at gardens")
        survival.command("data get entity ReviewMember Dimension", r"minecraft:overworld")
        mark = hub.mark()
        time.sleep(1.1)
        client.command("/telehop help", "/lobby takes you to the global hub")
        client.send("/lobby")
        hub.wait("ReviewMember joined the game", mark)
        client.command("/spawn gardens", "Arrived at gardens")
        assert_entity_values(survival, "Pos", [21.5, 96, -11.5])
        mark = hub.mark()
        time.sleep(1.1)
        client.send("/spawn")
        hub.wait("ReviewMember joined the game", mark)
        client.command("/spawn gardens", "Arrived at gardens")
        client.command("/spawn missing-destination", "destination is unavailable")
        survival.command("lp user ReviewMember permission set telehop.spawn.destination.gardens false", "false")
        client.command("/spawn gardens", r"(?i)permission")
        survival.command("lp user ReviewMember permission unset telehop.spawn.destination.gardens", r"(?i)unset|removed|no longer")
        survival.command("op ReviewMember", r"operator|opped")
        match = client.command("/telehop spawn delete staff-room", r"/telehop spawn delete staff-room ([0-9a-f-]{36})")
        client.command("/telehop spawn delete staff-room " + match[1], "Deleted staff-room")
        client.command("/spawn staff-room", "destination is unavailable")
        delegated_staff(stack, root, ports[0], client, survival)
        menu_travel(root, client, hub, survival)
        print("Passed YAML reload, exact placement, delegated staff actions, home and regional RTP menus with two clients.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("paper", type=Path, help="Completed Paper 1.21.11 smoke directory")
    parser.add_argument("bundle", type=Path)
    parser.add_argument("version")
    parser.add_argument("directory", type=Path)
    parser.add_argument("--proxy", choices=("velocity-3.5", "velocity-4.3.1"), default="velocity-3.5")
    parser.add_argument("--proxy-java", default="java")
    run(parser.parse_args())
