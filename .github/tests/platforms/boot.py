"""Exercise the packaged plugin on an exact Bukkit release and connect a protocol-matched player where a schema exists."""
import argparse
from contextlib import ExitStack
import os
from pathlib import Path
import re
import shutil
import socket
import sys
import time

NETWORK = Path(__file__).resolve().parents[1] / "network"
sys.path.insert(0, str(NETWORK))
from run import Process, assert_entity_values, entity_vector


def prepare(root, port, backend, hub, plugin, server, cache=None):
    root.mkdir(parents=True, exist_ok=False)
    (root / "eula.txt").write_text("eula=true\n")
    (root / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\n"
        'generator-settings={"biome":"minecraft:plains","layers":[{"height":1,"block":"minecraft:bedrock"},'
        '{"height":2,"block":"minecraft:dirt"},{"height":1,"block":"minecraft:grass_block"}]}\n'
        "level-type=minecraft:flat\nmax-tick-time=180000\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\ngenerate-structures=false\nlevel-seed=925\n")
    config = root / "plugins/TeleHop/config"
    config.mkdir(parents=True)
    # Exercise the upgrade from a configured split installation, including its existing database.
    (config / "database.yml").write_text(
        f"mysql: {{host: 127.0.0.1, port: {int(os.environ['MYSQL_PORT'])}, database: telehop_ci, "
        "username: telehop, password: ci-password, pool-size: 2}\n")
    (config / "general.yml").write_text(f"server-name: {backend}\nhub-server: {hub}\n")
    destination = f"landing-{backend}"
    (config / "spawns.yml").write_text(
        f"destinations:\n  {destination}: {{world: world, x: 4.5, y: 220, z: 8.5, yaw: 45, pitch: 12, access: public}}\n")
    shutil.copy(plugin, root / "plugins")
    shutil.copy(server, root / "server.jar")
    if cache:
        shutil.copytree(cache, root / "cache")
    return destination


def boot(stack, root):
    # The age reminder delays historic BuildTools binaries by 20 seconds per boot.
    return Process(stack, root, ["java", "-DIReallyKnowWhatIAmDoingISwear=true", "-Xms256m", "-Xmx1G", "-XX:ActiveProcessorCount=2",
                                "-jar", "server.jar", "--nogui"], "stop")


def ready(server, backend, platform):
    server.wait(r"Done \(", seconds=360)
    server.wait("Startup complete! Server: " + re.escape(backend), seconds=3)
    method = "Paper asynchronous" if platform in ("Purpur", "Paper") else "Bukkit main-thread"
    server.wait("Platform detection completed: " + re.escape(platform) + ", .*" + method, seconds=3)


def console_checks(server, backend, platform, game):
    ready(server, backend, platform)
    exact = r"Minecraft " + re.escape(game) + r", Bukkit API "
    server.wait(exact, seconds=3)
    server.command("telehop platform info", exact)
    server.command("telehop config validate", "active configuration is valid")
    server.command("telehop diagnostics", r"(?i)database circuit closed")
    server.command("telehop help", r"/spawn \[name\]")


def run(args):
    root = args.directory.resolve()
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    backend = args.platform.lower() + "-" + args.game.replace(".", "-")
    destination = prepare(root, port, backend, backend, args.plugin, args.server, args.cache)
    # Standalone protocol clients connect directly to a loopback server. Exercise its standalone respawn policy.
    (root / "plugins/TeleHop/config/messaging.yml").write_text("messaging: {network-enabled: false}\n")
    (root / "plugins/TeleHop/config/home.yml").write_text(
        "homes: {bed-set: WATER, bed-empty: AIR, bed-locked: MISSING_MATERIAL}\n")
    (root / "plugins/TeleHop/config/rtp.yml").write_text(
        "rtp: {max-radius: 48, dimensions: {surface: world}, default-dimension: surface, "
        "gui: {select-dimension-without-region: false}}\n")
    with ExitStack() as stack:
        first = boot(stack, root)
        console_checks(first, backend, args.platform, args.game)
    if first.proc.returncode != 0:
        raise AssertionError(f"{args.platform} stopped with exit status {first.proc.returncode}")
    first.log.rename(root / "boot-1.log")
    with ExitStack() as stack:
        server = boot(stack, root)
        console_checks(server, backend, args.platform, args.game)
        if not args.server_only:
            player_checks(stack, root, server, port, destination, args)
    if server.proc.returncode != 0:
        raise AssertionError(f"{args.platform} stopped with exit status {server.proc.returncode}")
    server.log.rename(root / "boot-2.log")
    print(f"{args.platform} {args.game} passed two boots, platform detection, database and command checks.")


def player_checks(stack, root, server, port, destination, args):
    bot_dir = root / "client"
    bot_dir.mkdir()
    name = "Test" + args.platform[0] + args.game.replace(".", "_")
    bot = Process(stack, bot_dir, ["node", str(NETWORK / "client.cjs"), str(port), name, args.game, args.client_schema], None)
    bot.wait("LOGIN_HANDSHAKE " + re.escape(args.game) + r" \d+", seconds=60)
    bot.wait("CLIENT_READY", seconds=60)
    server.command("minecraft:forceload add 4 8", r"(?i)marked chunk|already|no chunks")
    server.command("minecraft:fill 3 219 7 8 219 11 minecraft:stone", r"(?i)filled|no blocks")
    server.command(f"minecraft:tp {name} 7.5 220 10.5", "Teleported")
    bot.command("/telehop help", r"/spawn \[name\]")
    bot.command(f"/spawn {destination}", "Arrived at " + destination)
    assert_entity_values(server, "Pos", [4.5, 220, 8.5], name)
    assert_entity_values(server, "Rotation", [45, 12], name)
    server.command("minecraft:forceload add -29 -29 -11 -11", r"(?i)marked|already|no chunks")
    server.command("minecraft:fill -29 80 -29 -11 80 -11 minecraft:stone", r"(?i)filled|no blocks")
    server.command("minecraft:worldborder center -20 -20", r"(?i)border")
    server.command("minecraft:worldborder set 16", r"(?i)border")
    bot.command("/rtp", "Teleporting")
    assert_prepared_surface(server, name)
    bot.command("/sethome platform", "Home platform set")
    server.command("op " + name, r"(?i)operator|opped")
    bot.command("/listhomes " + name, r"CHAT_ACTION run_command /listhomes visit [a-f0-9-]+ platform")
    bot.command("/home", r"MENU .*lime_bed.*light_blue_bed")
    if args.game == "1.21.11" and args.platform == "CraftBukkit":
        server.command(f"minecraft:tp {name} -20 141 -20", "Teleported")
        before_death = bot.mark()
        server_before_death = server.mark()
        server.command("minecraft:kill " + name, r"(?i)killed")
        bot.wait("CLIENT_DEATH", before_death)
        bot.wait("CLIENT_RESPAWN", before_death)
        server.wait(r"\[RandomRespawn\] (?:Applying location|Late teleport) for " + name, server_before_death)
        assert_prepared_surface(server, name)
        print(f"{args.platform} returned a real player to the prepared surface after death.")
    print(f"{args.platform} passed named travel, safe random placement inside the world border, interactive chat and the home inventory.")


def assert_prepared_surface(server, name):
    deadline = time.monotonic() + 10
    while True:
        x, y, z = entity_vector(server, "Pos", name)
        if -28 < x < -12 and y == 81 and -28 < z < -12:
            break
        if time.monotonic() > deadline:
            raise AssertionError(f"TeleHop placed {name} at {(x, y, z)} outside the prepared surface")
        time.sleep(0.2)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=("CraftBukkit", "Spigot", "Purpur"))
    parser.add_argument("server", type=Path)
    parser.add_argument("plugin", type=Path)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--game", default="1.21.11")
    parser.add_argument("--client-schema", default="1.21.11")
    parser.add_argument("--server-only", action="store_true", help="Exercise lifecycle and console commands on the exact upstream build")
    parser.add_argument("--cache", type=Path, help="Optional local Paperclip vanilla cache")
    run(parser.parse_args())
