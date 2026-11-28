"""Exercise a member's paginated homes using real inventory clicks, commands and suggestions."""
import json
import re
import time


def check(client, backend, member):
    def command(text, expect):
        # Paper charges the same chat-spam budget for commands as for player chat.
        if text.startswith("/"):
            time.sleep(1.05)
        return client.command(text, expect)

    def grant(node, value=True):
        flag = str(value).lower()
        backend.command(f"lp user {member} permission set {node} {flag}", flag)

    def menu(action, occupied, next_page):
        result = command(action, r"MENU ([^\n]*)")[1]
        slots = {int(slot): item for slot, item in re.findall(r"(\d+)=(\w+)", result)}
        assert len([v for v in slots.values() if v == "lime_bed"]) == occupied, slots
        assert slots.get(44) == "barrier" and (slots.get(36) == "spectral_arrow") == next_page, slots
        return slots

    def title(page):
        actual = json.loads(command(":title", r"MENU_TITLE ([^\n]+)")[1])
        assert actual == "Your Homes" + " " * 17 + f"Page {page}", actual

    def label(slot, name, colour):
        raw = command(f":item {slot}", r"ITEM_NAME ([^\n]+)")[1]
        assert name in raw and colour in raw and "TeleHop" not in raw, raw

    def complete(line):
        return json.loads(command(":complete " + line, r"COMPLETIONS (\[[^\n]*\])")[1])

    # Keep this player separate from the operator and other network acceptance accounts.
    initial = menu("/home", 0, False)
    assert list(initial.values()).count("red_bed") == 1 and list(initial.values()).count("light_blue_bed") == 9
    title(1)
    label(44, "Exit", "red")
    client.send(":close")
    command("/sethome H01", "Home H01 set")
    command("/sethome default_denied", "slots are full")
    grant("telehop.homes.20", False)
    grant("telehop.homes.17")
    for i in range(1, 11):
        command(f"/sethome H{i:02}", f"Home H{i:02} set")
    menu("/homes", 10, True)
    empty = menu(":click 36 spectral_arrow", 0, False)
    assert list(empty.values()).count("red_bed") == 7
    client.send(":close")
    for i in range(11, 17):
        command(f"/sethome H{i}", f"Home H{i} set")
    command("/sethome North Gate", "Home North Gate set")
    command("/sethome above_limit", "slots are full")
    command("/sethome H01", "Home H01 set")
    menu("/home", 10, True)
    label(36, "Next Page", "gold")
    slots = menu(":click 36 spectral_arrow", 7, False)
    title(2)
    label(44, "Back", "red")
    bed = next(slot for slot, item in slots.items() if item == "lime_bed")
    command(f":click {bed} lime_bed", "12=lime_wool")
    menu(":click 26 barrier", 7, False)
    menu(":click 44 barrier", 10, True)
    title(1)
    client.send(":close")
    assert sorted(complete("/homes H1")) == [f"H{i}" for i in range(10, 17)]
    assert complete("/home Nor") == ["North Gate"]
    assert complete("/homes North G") == ["Gate"]
    expected = {f"H{i:02}" for i in range(1, 17)} | {"North Gate"}
    assert set(complete("/homes ")) == expected
    for root in ("home", "homes"):
        command("/" + root + " North Gate", "Teleporting to North Gate")
    menu("/homes", 10, True)
    grant("telehop.homes.10")
    grant("telehop.homes.17", False)
    # A stale arrow after a rank change must return to the permitted first page.
    menu(":click 36 spectral_arrow", 10, False)
    title(1)
    label(44, "Exit", "red")
    client.send(":close")
    menu("/homes", 10, False)
    client.send(":close")
    command("/sethome denied", "slots are full")
    command("/homes H16", "Teleporting to H16")
    grant("telehop.homes.240")
    command("/sethome high_rank", "Home high_rank set")
    grant("telehop.homes.240", False)
    grant("telehop.homes.unlimited")
    command("/sethome unlimited_rank", "Home unlimited_rank set")
    grant("telehop.homes.unlimited", False)
    grant("telehop.homes.20")
    menu("/homes", 10, True)
    menu(":click 36 spectral_arrow", 9, False)
    title(2)
    label(44, "Back", "red")
    client.send(":close")
    time.sleep(1.1)
    mark = client.mark()
    client.send("/sethome racing_one")
    client.send("/sethome racing_two")
    client.wait(r"Home racing_(one|two) set", mark)
    client.wait("slots are full", mark)
    assert len(set(complete("/homes ")) & {"racing_one", "racing_two"}) == 1
    grant("telehop.homes.unlimited")
    menu("/homes", 10, True)
    menu(":click 36 spectral_arrow", 10, True)
    empty = menu(":click 36 spectral_arrow", 0, True)
    assert list(empty.values()).count("red_bed") == 10
    menu(":click 11 red_bed", 1, True)
    title(3)
    label(11, "Home 21", "green")
    menu(":click 36 spectral_arrow", 0, True)
    menu(":click 44 barrier", 1, True)
    client.send(":close")
    command("/homes Home 21", "Teleporting to Home 21")
    print("Verified 10 and 17 homes, navigation, both roots, personalised completion and rank changes.", flush=True)
