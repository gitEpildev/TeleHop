"""Expected TeleHop diagnostic for a known real backend in the compatibility matrix."""
import re


def signature(platform, game):
    chat, operations = {
        "CraftBukkit": ("Bukkit and vanilla component chat", "Bukkit main-thread teleport and chunks"),
        "Spigot": ("Spigot component bridge", "Bukkit main-thread teleport and chunks"),
        "Paper": ("Spigot component bridge", "Paper asynchronous teleport and chunks"),
        "Purpur": ("Spigot component bridge", "Paper asynchronous teleport and chunks"),
    }[platform]
    adapter = "Paper121Adapter" if game.split(".")[:2] == ["1", "21"] else "Paper261Adapter"
    return (re.escape(f"{platform}, Minecraft {game}, Bukkit API ") + r"[^,\r\n]+, Java [0-9]+, "
            + re.escape(f"{adapter}, {operations}, ") + r"[^\r\n]*?, chat=" + re.escape(chat) + r"(?=\r?\n|$)")
