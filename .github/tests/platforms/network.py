"""Exercise public named travel in a single Velocity network of heterogeneous backend JARs."""
import argparse
from contextlib import ExitStack
from pathlib import Path
import socket

from boot import boot, prepare, ready, NETWORK
from run import Process, assert_entity_values, proxy, distribution_name


def run(args):
    root = args.directory.resolve()
    root.mkdir(parents=True, exist_ok=False)
    platforms = {"inlet": ("CraftBukkit", args.craftbukkit), "forge": ("Spigot", args.spigot), "observatory": ("Paper", args.paper)}
    with ExitStack() as reservations:
        ports = []
        for _ in range(1 + len(platforms)):
            sock = reservations.enter_context(socket.socket())
            sock.bind(("127.0.0.1", 0))
            ports.append(sock.getsockname()[1])
    with ExitStack() as stack:
        servers = {}
        for (name, (platform, jar)), port in zip(platforms.items(), ports[1:], strict=True):
            prepare(root / name, port, name, "inlet", args.bundle / distribution_name("paper", args.version), jar,
                    args.cache if platform == "Paper" else None)
            servers[name] = boot(stack, root / name)
        bridge = proxy(stack, args, root, ports, server_names=tuple(platforms))
        bridge.wait(r"Done \(", seconds=150)
        for name, server in servers.items():
            ready(server, name, platforms[name][0], "1.21.11")
            server.command("minecraft:forceload add 4 8", r"(?i)marked chunk|already|no chunks")
            server.command("minecraft:fill 3 219 7 8 219 11 minecraft:stone", r"(?i)filled|no blocks")
        client_dir = root / "client"
        client_dir.mkdir()
        player = "PlatformJourney"
        client = Process(stack, client_dir, ["node", str(NETWORK / "client.cjs"), str(ports[0]), player], None)
        client.wait("CLIENT_READY", seconds=60)
        for name in ("forge", "observatory", "inlet"):
            mark = servers[name].mark()
            client.command("/spawn landing-" + name, "Arrived at landing-" + name)
            servers[name].wait(player + " joined the game", mark)
            assert_entity_values(servers[name], "Pos", [4.5, 220, 8.5], player)
            assert_entity_values(servers[name], "Rotation", [45, 12], player)
    print("Named travel passed from CraftBukkit to Spigot to Paper and back through Velocity.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("craftbukkit", type=Path)
    parser.add_argument("spigot", type=Path)
    parser.add_argument("paper", type=Path)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("version")
    parser.add_argument("directory", type=Path)
    parser.add_argument("--cache", type=Path)
    parser.set_defaults(proxy="velocity-3.5", proxy_java="java")
    run(parser.parse_args())
