"""Public distribution names. Platform IDs also identify the unchanged Maven modules and loader inventories."""

PLATFORMS = ("paper", "velocity")


def filename(platform, version):
    if platform not in PLATFORMS:
        raise ValueError(f"Unknown TeleHop distribution platform: {platform}")
    name = "bukkit" if platform == "paper" and int(version.split(".", 1)[0]) >= 4 else platform
    return f"telehop-{name}-{version}.jar"


def modrinth_title(platform, version, loaders):
    if platform == "paper" and int(version.split(".", 1)[0]) >= 4:
        names = ["CraftBukkit" if loader == "bukkit" else loader.title() for loader in loaders]
        supported = names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1]
        return f"TeleHop {version} - Bukkit backend ({supported})"
    return f"TeleHop {version} ({platform.title()})"
