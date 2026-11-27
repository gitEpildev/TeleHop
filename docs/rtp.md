# Random teleportation

The active deployment profile's `rtp.yml` defines regions, menu appearance, dimension IDs, destination worlds, the shared cooldown and the warmup. The normal `/rtp` command opens the selection menu. With a single eligible region it opens the dimension selector directly. With no regions, fresh profiles show the dimension selector. Existing profiles without `rtp.gui.select-dimension-without-region` travel directly to their default dimension. Each backend generates safe coordinates using its own world and radius limits.

## Configure a direct command

In this example the administrator owns a region called `islands`, a backend called `coast` and a dimension called `caves`. Add the destination on participating backends. Use the registered backend name from Velocity.

```yaml
# Active profile general.yml
servers: {islands: coast}
```

```yaml
# Active profile rtp.yml
rtp:
  max-radius: 4000
  default-region: islands
  default-dimension: caves
  regions:
    islands: {radius: 1000, gui: {material: OAK_BOAT, name: Islands}}
  dimensions: {caves: island_caves}
```

Enable `commands.additional-commands.enabled` in the root `config.yml`. Add this definition to `commands/rtp.yml` on both the source and destination.

```yaml
rtp:
  additional-commands:
    enabled: true
    commands:
      island-trip:
        enabled: true
        name: islandhop
        aliases: [ishop]
        destination: {region: islands, dimension: caves}
        gui: {enabled: false}
        settings:
          inherit-defaults: true
          overrides: {delay-seconds: 5, cancel-on-move: true, max-radius: 600}
        permissions:
          require-command-permission: true
          require-region-permission: true
          require-dimension-permission: true
```

Restart the backend. A player with `telehop.rtp`, `telehop.command.island-trip`, `telehop.rtp.region.islands` and `telehop.rtp.dimension.caves` can now use `/islandhop` or `/ishop`. The destination must load the `island_caves` world. With `gui.enabled: true` the player selects from permitted choices consistent with the command’s destination. Configured dimensions may use arbitrary IDs.

## Routing and limits

- Select a region or an explicit registered server. Optionally add a configured dimension or world. Combining region with server, or dimension with world, is ambiguous and rejected.
- A region uses its explicit server mapping. A matching topology region without a direct mapping uses its first configured backend. An RTP region without a network mapping is local. An explicitly selected server must appear in this backend's configuration.
- With an empty `dimensions` map, TeleHop lists the backend's loaded worlds, including Multiverse worlds. Reload after adding a world. To keep the same IDs across backends with different world names, supply an explicit map. Use `rtp.gui.dimension-items.<id>` to set a material, display name and lore. Large catalogues use multiple pages. Unknown materials and blocks that cannot appear as inventory items use a grass block icon.
- A direct dimension-only or world-only command uses the current backend. A command with no world or dimension inherits `rtp.default-dimension`. A region can override the world for that dimension. Ordinary arrivals with missing selectors use `rtp.default-region` and `rtp.default-dimension`. When either is absent, the first entry in the corresponding map is the default. The first loaded world is the default for an automatically discovered catalogue.
- A missing override inherits the active profile's cooldown, delay, movement cancellation or region radius. `max-radius` in a command can override the region radius up to the backend's profile-wide maximum. All variants use the same local player cooldown and warmup guard. Bypass permissions retain their existing meaning.
- The destination resolves its own command definition, checks current permissions and verifies the loaded world before searching. Configure the same identifiers on every participating backend. A failed placement is reported as a failure. Cross-server variants require V4 on both backends and the proxy.
- On an existing registration, use `/telehop reload commands` to apply changed selectors, permission requirements and overrides. Restart to add or remove a registered command or alias. The previous rules remain active after an invalid reload.
