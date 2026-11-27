"""Log in with a shared Minecraft wire protocol and use TeleHop through a real Velocity proxy."""
import argparse
from contextlib import ExitStack
import hashlib
from pathlib import Path
import socket

from run import Process, assert_entity_values, paper, proxy
from server_smoke import COMPATIBILITY


def run(args):
    test = COMPATIBILITY["velocity_client_backends"][args.client_version]
    backend_version = test["backend"]
    args.proxy = "velocity-" + test["proxy"]
    digest = hashlib.sha256((args.paper / "server.jar").read_bytes()).hexdigest()
    if digest != COMPATIBILITY["paper"][backend_version]["sha256"]:
        raise ValueError(f"Expected the verified Paper {backend_version} smoke directory")
    root = args.directory.resolve()
    root.mkdir(parents=True, exist_ok=False)
    with ExitStack() as reserved:
        sockets = [reserved.enter_context(socket.socket()) for _ in range(2)]
        for sock in sockets:
            sock.bind(("127.0.0.1", 0))
        ports = [sock.getsockname()[1] for sock in sockets]
    with ExitStack() as stack:
        spawns = ("destinations:\n  protocol-test: "
                  "{world: world, x: 7.5, y: 220, z: 8.5, yaw: 45, pitch: 12, access: public}\n")
        backend = paper(stack, args, "hub", ports[1], root, spawns=spawns, extra_plugins=())
        bridge = proxy(stack, args, root, ports)
        for service in (backend, bridge):
            service.wait(r"Done \(", seconds=180)
            service.wait("Startup complete!", seconds=60)
        name = "ProtocolReview"
        directory = root / "client"
        directory.mkdir()
        client = Process(stack, directory, ["node", str(Path(__file__).with_name("client.cjs").resolve()),
                                           str(ports[0]), name, args.client_version, test["schema"]], None)
        handshake = client.wait(r"LOGIN_HANDSHAKE ([\d.]+) (\d+)", seconds=30)
        assert handshake[1] == args.client_version
        client.wait("CLIENT_READY", seconds=150)
        backend.wait(name + " joined the game")
        # Support the starting position as well as the destination before moving the client.
        backend.command("minecraft:forceload add 7 8", r"(?i)marked .*chunk|already|no chunks")
        backend.command("minecraft:fill 6 219 7 9 219 11 minecraft:stone", r"(?i)filled|blocks")
        backend.command(f"minecraft:tp {name} 7.5 220 10.5", "Teleported")
        client.command("/spawn protocol-test", "Arrived at protocol-test")
        assert_entity_values(backend, "Pos", (7.5, 220, 8.5), name)
        assert_entity_values(backend, "Rotation", (45, 12), name)
        print(f"Minecraft {args.client_version} wire protocol {handshake[2]} logged in through {args.proxy} "
              f"to Paper {backend_version} and completed a named spawn.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paper", type=Path)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("version")
    parser.add_argument("directory", type=Path)
    parser.add_argument("client_version", choices=COMPATIBILITY["velocity_client_backends"])
    parser.set_defaults(proxy_java="java")
    run(parser.parse_args())
