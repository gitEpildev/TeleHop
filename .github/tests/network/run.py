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
from server_smoke import SERVERS, mysql

PLUGINS = {
    "LuckPerms.jar": ("https://cdn.modrinth.com/data/Vebnzrzj/versions/b0mk8uS6/LuckPerms-Bukkit-5.5.71.jar",
                      "49cecb66fa1fd22a133039a490e9c1e5095a238e7cd66eb9d2a16fe6c897550d"),
    "Multiverse-Core.jar": ("https://cdn.modrinth.com/data/3wmN97b8/versions/de7TbcZG/multiverse-core-5.8.2.jar",
                            "792252b6bbcf17c94ab8385e595281c830d7a40c946aea1832d8a010702bb630"),
}


def download(destination, url, digest):
    subprocess.run(["curl", "-fsSL", "--retry", "3", "-o", destination, url], check=True)
    if hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
        raise ValueError(f"Checksum mismatch for {destination.name}")


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
        if text.startswith(("/spawn", "/hub")):
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


def paper(stack, args, name, port, root, *, general=None, spawns=None, storage=None, rtp=None,
          folder_name=None, plugin_jar=None, extra_plugins=PLUGINS):
    folder = root / (folder_name or name)
    folder.mkdir()
    template = args.paper
    for world in ("world", "world_nether", "world_the_end"):
        shutil.copytree(template / world, folder / world)
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
    (config / "general.yml").write_text(general or f"server-name: {name}\nhub-server: hub\n")
    if spawns is None:
        (config / "features.yml").write_text("features: {spawn: true, hub: true}\n")
        if name == "survival":
            (config / "commands.yml").write_text("hub: [lobby, hub]\n")
    default_spawns = (
        "routing:\n  enabled: true\n  default: {server: hub}\n"
        '  worlds: {training: {server: survival, spawn: gardens}}\n')
    if name == "hub":
        default_spawns = "destinations: {}\n"
    (config / "spawns.yml").write_text(spawns or default_spawns)
    if rtp:
        (config / "rtp.yml").write_text(rtp)
    if storage:
        (config.parent / "storage.yml").write_text(storage)
    shutil.copy(plugin_jar or args.bundle / f"telehop-paper-{args.version}.jar", folder / "plugins")
    for filename in extra_plugins:
        shutil.copy(root / filename, folder / "plugins")
    return Process(stack, folder, ["java", "-Xms256m", "-Xmx1000m", "-XX:ActiveProcessorCount=2",
                                  "-jar", "server.jar", "--nogui"], "stop")


def proxy(stack, args, root, ports, *, folder_name="proxy", settings="", plugin_jar=None):
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
    backends = f'[servers]\nhub = "127.0.0.1:{ports[1]}"\n'
    if len(ports) == 3:
        backends += f'survival = "127.0.0.1:{ports[2]}"\n'
    toml = re.sub(r'\[servers\].*?\[forced-hosts\]', backends + 'try = ["hub"]\n\n[forced-hosts]', toml, flags=re.S)
    toml = re.sub(r'\[forced-hosts\].*?(?=\n\[)', '[forced-hosts]\n', toml, flags=re.S)
    (folder / "velocity.toml").write_text(toml)
    (folder / "forwarding.secret").write_text(secrets.token_hex(32))
    config = folder / "plugins/telehop-velocity"
    config.mkdir(parents=True)
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
    general = root / "hub/plugins/TeleHop/config/general.yml"
    general.write_text("server-name: hub\nhub-server: hub\nservers: {eu: hub, usa: survival}\n")
    destination_rtp = root / "survival/plugins/TeleHop/config/rtp.yml"
    destination_rtp.write_text("rtp:\n  max-radius: 32\n  regions: {usa: {radius: 32}}\n")
    survival.command("minecraft:forceload add -32 -32 32 32", r"(?i)marked .*chunk|already")
    survival.command("minecraft:fill -32 200 -32 32 200 32 minecraft:stone", r"(?i)filled|blocks")
    client.command("/telehop reload", r"(?i)reloaded")
    regional_rtp(client, "ReviewMember", survival, radius=32, landing_y=201)


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
        hub = paper(stack, args, "hub", ports[1], root)
        survival = paper(stack, args, "survival", ports[2], root)
        bridge = proxy(stack, args, root, ports)
        for server in (hub, survival, bridge):
            server.wait(r"Done \(", seconds=180)
        client_dir = root / "client"
        client_dir.mkdir()
        client = Process(stack, client_dir, ["node", str(Path(__file__).with_name("client.cjs").resolve()), str(ports[0])], None)
        client.wait("CLIENT_READY", seconds=45)
        hub.command("op ReviewMember", r"operator|opped")
        client.command("/telehop spawn setdefault", "Saved the default spawn")
        client.command("/spawn", "Teleporting to spawn")
        assert mysql("SELECT default_world, routing_enabled, hub_command FROM spawn_backends WHERE server='hub'") == ["world\t0\t1"]
        # Registration and the shared directory must still describe the running Paper instance after a reload.
        switches = root / "hub/plugins/TeleHop/config/features.yml"
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
        survival.command("mv create training normal", r"(?i)world 'training' created")
        survival.command("mv tp ReviewMember training", r"(?i)teleport")
        time.sleep(1)
        client.command("/telehop spawn set staff-room restricted", "Saved staff-room")
        # This name is created exclusively by editing YAML while the servers are running.
        chosen = "orchard-" + secrets.token_hex(3)
        settings = root / "survival/plugins/TeleHop/config/spawns.yml"
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
