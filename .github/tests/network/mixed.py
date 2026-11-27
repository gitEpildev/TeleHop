"""Check player ownership on two live Velocity generations sharing Paper, Redis and MySQL."""
import argparse
from contextlib import ExitStack
from pathlib import Path
import re
import socket
import subprocess
import time

from run import PLUGINS, Process, assert_entity_values, download, mysql, paper, proxy, regional_rtp, regional_settings


# Names and coordinates deliberately differ from the regional and global defaults.
BACKENDS = {
    "hub":      ("harbour", (21.5, 260, -11.5, 33, -12), (24.5, 260, -12.5, 90, 10)),
    "survival": ("orchard", (25.5, 260, -10.5, 63, -25), (22.5, 260, -13.5, 15, 18)),
}
OBSERVER_POS = [19.5, 260, -9.5]
TIMEOUT_MS = 3500
RELEASE = "https://github.com/gitEpildev/TeleHop/releases/download/v2.3.0"
LEGACY_DIGESTS = {
    "velocity": "25a6fb21bb15a8b3f57ebb746f65e48a4a12475b819ab94f36b89e7e7ff27b35",
    "paper": "4bdc895a83e59a7178d2557560e753e0dec36758b6e7b203bf6d524889ae5ca4",
}


def point_yaml(point):
    x, y, z, yaw, pitch = point
    return f"world: world, x: {x}, y: {y}, z: {z}, yaw: {yaw}, pitch: {pitch}"


def backend(stack, args, root, name, port):
    identifier, coords, default = BACKENDS[name]
    general = (f"server-name: {name}\nhub-server: survival\nservers: {{port: hub, mines: survival}}\n"
               "multi-proxy: {enabled: true}\n")
    spawns = ("routing: {enabled: false}\n"
              f"destinations:\n  {identifier}: {{{point_yaml(coords)}, access: public}}\n")
    rtp = regional_settings(8, dimension="overworld")
    return paper(stack, args, name, port, root, general=general, spawns=spawns, rtp=rtp,
                 profile="multi", features="features: {hub: true, spawn: true}\n",
                 regions="regions: {review: {servers: [hub, survival], hub: hub}}\n",
                 messaging=f"messaging: {{request-timeout-ms: {TIMEOUT_MS}}}\n",
                 storage="spawn: {" + point_yaml(default) + "}\n")


def start_proxy(stack, args, root, generation, ports, redis_port, *, legacy=None, instance=None):
    name = instance or ("legacy-2.3" if legacy else f"velocity-{generation}")
    run_args = argparse.Namespace(**vars(args))
    run_args.proxy = {3: "velocity-3.5", 4: "velocity-4.3.1"}[generation]
    run_args.proxy_java = "java" if generation == 3 else args.java25
    settings = (f"multi-proxy.enabled=true\nproxy.id={name}\nredis.port={redis_port}\n"
                f"redis.channel-prefix=mixed\nmessaging.requestTimeoutMs={TIMEOUT_MS}\n"
                "servers.hub=survival\nservers.backends=hub,survival\n")
    return proxy(stack, run_args, root, ports, folder_name=name, settings=settings, plugin_jar=legacy)


def client(stack, root, port, name):
    directory = root / name
    directory.mkdir()
    bot = Process(stack, directory, ["node", str(Path(__file__).with_name("client.cjs").resolve()),
                                      str(port), name], None)
    bot.wait("CLIENT_READY", seconds=150)
    return bot


def check_players(server, expected):
    # Paper lists players in join order. The first carrier on each backend belongs to the other proxy.
    match = server.command("minecraft:list", r"players online: ([^\r\n]+)")
    actual = [name.strip() for name in match[1].split(",")]
    if actual != expected:
        raise AssertionError(f"{server.log}: expected join order {expected}, got {actual}")


def placement(server, player, point):
    server.command(f"data get entity {player} Dimension", r"minecraft:overworld")
    assert_entity_values(server, "Pos", point[:3], player)
    assert_entity_values(server, "Rotation", point[3:], player)


def move(bot, player, command, dest, *, arrived=None, join_timeout=30):
    log_mark = dest.mark()
    time.sleep(1.1)  # The preceding command may have started TeleHop's per-player debounce.
    player_mark = bot.mark()
    bot.send(command)
    dest.wait(re.escape(player) + " joined the game", log_mark, seconds=join_timeout)
    if arrived:
        bot.wait("Arrived at " + re.escape(arrived), player_mark, seconds=15)


def check_clean_log(bot, mark):
    text = bot.log.read_text(errors="replace")[mark:]
    if re.search(r"destination is unavailable|\bKICK\b", text):
        raise AssertionError(f"{bot.log}: transfer failed or timed out after arrival\n{text}")


def rtp_ground(survival):
    survival.command("minecraft:forceload add -8 -8 8 8", r"(?i)marked .*chunk|no chunks were marked")
    for _ in range(30):
        result = survival.command("minecraft:fill -8 240 -8 8 240 8 minecraft:stone",
                                  r"(?i)successfully filled|no blocks were filled|position is not loaded")
        if "not loaded" not in result[0].lower():
            return
        time.sleep(0.2)
    raise AssertionError("Paper did not load the test region for the RTP platform")


def exercise(root, servers, request_port, observer_port, label, *, legacy=False):
    hub, survival = servers
    home_guest, away_guest, name = (prefix + label for prefix in ("HubGuest", "AwayGuest", "Traveller"))
    with ExitStack() as clients:
        away = client(clients, root, observer_port, away_guest)
        move(away, away_guest, "/server survival", survival)
        home = client(clients, root, observer_port, home_guest)
        for server, guest in zip(servers, (home_guest, away_guest), strict=True):
            server.command("minecraft:forceload add 19 -10", r"(?i)marked chunk|no chunks were marked")
            server.command(f"minecraft:tp {guest} 19.5 260 -9.5", "Teleported")
            server.command("minecraft:fill 18 259 -15 28 259 -8 minecraft:stone",
                           r"(?i)filled|already|no blocks")
            server.command(f"minecraft:tp {guest} 19.5 260 -9.5", "Teleported")
            check_players(server, [guest])
        bot = client(clients, root, request_port, name)
        check_players(hub, [home_guest, name])
        begin = bot.mark()
        if legacy:
            # The first player has a capable proxy but the requester's TeleHop 2.3.0 proxy does not.
            hub.command(f"minecraft:tp {name} 20.5 260 -9.5 0 0", "Teleported")
            bot.command("/spawn orchard", "destination is unavailable")
            check_players(hub, [home_guest, name])
            check_players(survival, [away_guest])
            placement(hub, name, (20.5, 260, -9.5, 0, 0))
            move(bot, name, "/server survival", survival)
        else:
            move(bot, name, "/spawn orchard", survival, arrived="orchard")
            check_players(survival, [away_guest, name])
            placement(survival, name, BACKENDS["survival"][1])
        # A bare /spawn goes to the regional hub. /hub has a different global target.
        move(bot, name, "/spawn", hub)
        placement(hub, name, BACKENDS["hub"][2])
        if not legacy:
            move(bot, name, "/hub", survival)
            placement(survival, name, BACKENDS["survival"][2])
            move(bot, name, "/spawn harbour", hub, arrived="harbour")
            placement(hub, name, BACKENDS["hub"][1])
            # A misplaced destination acknowledgement can arrive as a late failure after placement.
            time.sleep(TIMEOUT_MS / 1000 + 1)
            check_clean_log(bot, begin)
            regional_rtp(bot, name, survival, radius=8, landing_y=241)
            check_players(survival, [away_guest, name])
        for server, guest, witness in zip(servers, (home_guest, away_guest), (home, away), strict=True):
            assert_entity_values(server, "Pos", OBSERVER_POS, guest)
            if "Arrived at " in witness.log.read_text(errors="replace"):
                raise AssertionError(f"{guest} received another player's arrival")
        if not legacy:
            # The requesting moderator and moving player are connected through different proxies.
            move(bot, name, "/server hub", hub)
            survival.command("minecraft:op " + away_guest, "(?i)made .* operator|already")
            arrival = survival.mark()
            away.send("/tphere " + name)
            survival.wait(name + " joined the game", arrival)
            assert_entity_values(survival, "Pos", OBSERVER_POS, name)
            survival.command("minecraft:deop " + away_guest, "(?i)no longer|removed|not an operator")
        departing = [server.mark() for server in servers]
    hub.wait(home_guest + " left the game", departing[0])
    survival.wait(away_guest + " left the game", departing[1])
    if legacy:
        hub.wait(name + " left the game", departing[0])
    else:
        survival.wait(name + " left the game", departing[1])
    print(f"Passed {label}: checked real player placement, observer isolation and regional routing.", flush=True)


def three_proxy_requests(root, servers, requester_port, accepter_port, observer_port):
    hub, survival = servers
    sender_name, accepter_name, observer_name = "TpaSender", "TpaRecipient", "TpaObserver"
    hub_point = BACKENDS["hub"][1]
    survival_point = BACKENDS["survival"][1]
    with ExitStack() as clients:
        observer = client(clients, root, observer_port, observer_name)
        move(observer, observer_name, "/server survival", survival)
        survival.command(f"minecraft:tp {observer_name} 19.5 260 -9.5", "Teleported")
        accepter = client(clients, root, accepter_port, accepter_name)
        move(accepter, accepter_name, "/server survival", survival)
        survival.command(f"minecraft:tp {accepter_name} " + " ".join(map(str, survival_point)), "Teleported")
        sender = client(clients, root, requester_port, sender_name)
        check_players(survival, [observer_name, accepter_name])

        notice = accepter.mark()
        sender.command("/tpa " + accepter_name, "Teleport request sent")
        accepter.wait(sender_name + " wants to teleport to you", notice)
        arrival = survival.mark()
        accepter.command("/tpaaccept", "Teleport request accepted")
        survival.wait(sender_name + " joined the game", arrival, seconds=30)
        placement(survival, sender_name, survival_point)
        assert_entity_values(survival, "Pos", OBSERVER_POS, observer_name)

        # Use a fresh requester so the second direction exercises the default cooldown.
        return_name = "TpaHereSender"
        return_sender = client(clients, root, requester_port, return_name)
        hub.command(f"minecraft:tp {return_name} " + " ".join(map(str, hub_point)), "Teleported")
        check_players(survival, [observer_name, accepter_name, sender_name])
        notice = accepter.mark()
        return_sender.command("/tpahere " + accepter_name, "Teleport request sent")
        accepter.wait(return_name + " wants you to teleport to them", notice)
        arrival = hub.mark()
        accepter.command("/tpaaccept", "Teleport request accepted")
        hub.wait(accepter_name + " joined the game", arrival, seconds=30)
        placement(hub, accepter_name, hub_point)
        assert_entity_values(survival, "Pos", OBSERVER_POS, observer_name)
        departing = [server.mark() for server in servers]
    hub.wait(accepter_name + " left the game", departing[0])
    survival.wait(observer_name + " left the game", departing[1])
    print("Passed three entry proxies: /tpa and /tpahere placed the correct players; the first bystander stayed put.",
          flush=True)


def subscribers(port, count):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        lines = subprocess.check_output(["redis-cli", "--raw", "-p", str(port), "PUBSUB", "NUMSUB",
                                         "mixed:cross-proxy"], text=True).splitlines()
        if lines == ["mixed:cross-proxy", str(count)]:
            return
        time.sleep(0.2)
    raise AssertionError(f"Redis did not register {count} proxy subscribers: {lines!r}")


def legacy_destination(stack, args, root, hub, target_port, entry_port, old_jar):
    # A rolled-back backend leaves a valid-looking published row but cannot place named arrivals.
    assert mysql("SELECT server FROM named_spawns WHERE id='orchard'") == ["survival"]
    old = paper(stack, args, "survival", target_port, root, folder_name="legacy-paper", plugin_jar=old_jar)
    old.wait(r"Done \(", seconds=180)
    old.wait(r"\[TeleHop\] Startup complete! Server: survival\b", seconds=2)
    name = "LegacyBackend"
    bot = client(stack, root, entry_port, name)
    # This is the rollback backend's first client login after a cold boot.
    move(bot, name, "/server survival", old, join_timeout=150)
    old.command("minecraft:forceload add 19 -10", r"(?i)marked chunk|already")
    old.command(f"minecraft:tp {name} 19.5 260 -9.5 12 5", "Teleported")
    old.command("minecraft:setblock 19 259 -10 minecraft:stone", r"(?i)changed|already|could not set")
    old.command(f"minecraft:tp {name} 19.5 260 -9.5 12 5", "Teleported")
    placement(old, name, (*OBSERVER_POS, 12, 5))
    move(bot, name, "/server hub", hub)
    begin = bot.mark()
    move(bot, name, "/spawn orchard", old)
    bot.wait("destination is unavailable", begin, seconds=20)
    placement(old, name, (*OBSERVER_POS, 12, 5))
    if "Arrived at orchard" in bot.log.read_text(errors="replace")[begin:]:
        raise AssertionError("An unsupported backend reported a successful placement")
    move(bot, name, "/spawn", hub)
    placement(hub, name, BACKENDS["hub"][2])
    print("Passed the 2.3.0 Paper rollback: named arrival refused and legacy return trip completed.", flush=True)


def run(args):
    root = args.directory.resolve()
    root.mkdir(parents=True, exist_ok=False)
    for name, (url, digest) in PLUGINS.items():
        download(root / name, url, digest)
    legacy = {}
    for platform, digest in LEGACY_DIGESTS.items():
        jar = root / f"telehop-{platform}-2.3.0.jar"
        download(jar, f"{RELEASE}/{jar.name}", digest)
        legacy[platform] = jar
    with ExitStack() as reserved:
        sockets = [reserved.enter_context(socket.socket()) for _ in range(6)]
        for sock in sockets:
            sock.bind(("127.0.0.1", 0))
        hub, survival, v3, v4, old, redis = [sock.getsockname()[1] for sock in sockets]
    with ExitStack() as stack:
        directory = root / "redis"
        directory.mkdir()
        service = Process(stack, directory, ["redis-server", "--bind", "127.0.0.1", "--port", str(redis),
                                              "--save", "", "--appendonly", "no"], None)
        service.wait("Ready to accept connections")
        servers = [backend(stack, args, root, name, port)
                   for name, port in (("hub", hub), ("survival", survival))]
        proxies = [start_proxy(stack, args, root, gen, [port, hub, survival], redis)
                   for gen, port in ((3, v3), (4, v4))]
        for process in servers + proxies:
            process.wait(r"Done \(", seconds=180)
        subscribers(redis, 2)
        rtp_ground(servers[1])
        exercise(root, servers, v3, v4, "V3")
        exercise(root, servers, v4, v3, "V4")
        with ExitStack() as witness_stack:
            witness = start_proxy(witness_stack, args, root, 3, [old, hub, survival], redis, instance="observer-proxy")
            witness.wait(r"Done \(", seconds=100)
            subscribers(redis, 3)
            three_proxy_requests(root, servers, v3, v4, old)
        subscribers(redis, 2)
        with ExitStack() as old_proxy_stack:
            old_proxy = start_proxy(old_proxy_stack, args, root, 3, [old, hub, survival], redis, legacy=legacy["velocity"])
            old_proxy.wait(r"Done \(", seconds=100)
            subscribers(redis, 3)
            exercise(root, servers, old, v4, "Legacy", legacy=True)
        servers[1].close()
        legacy_destination(stack, args, root, servers[0], survival, v3, legacy["paper"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paper", type=Path, help="Completed Paper 1.21.11 smoke directory")
    parser.add_argument("bundle", type=Path)
    parser.add_argument("version")
    parser.add_argument("directory", type=Path)
    parser.add_argument("--java25", required=True, help="Java 25 executable for Velocity 4")
    run(parser.parse_args())
