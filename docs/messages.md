# Messages Customization

All player-facing messages are stored in language files inside `plugins/TeleHop/languages/` and use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format.

## Language System

TeleHop ships with 6 built-in languages:

| Code | Language | File |
|------|----------|------|
| `en` | English | `languages/en.yml` |
| `nl` | Dutch | `languages/nl.yml` |
| `de` | German | `languages/de.yml` |
| `es` | Spanish | `languages/es.yml` |
| `zh` | Chinese (Simplified) | `languages/zh.yml` |
| `pl` | Polish | `languages/pl.yml` |

Set your language in `config.yml`:

```yaml
language: "en"
```

Change it and run `/telehop reload` -- no restart needed.

### Fallback Behavior

If a message key is missing from the selected language file, TeleHop automatically falls back to the English (`en.yml`) value. This means partial translations work fine -- you only need to translate the keys you care about.

### Custom Languages

Copy `en.yml` to a new file (e.g. `fr.yml`), translate the values, set `language: "fr"`, and reload.

## Quick Color Reference

| Tag | Result |
|-----|--------|
| `<red>` | Red text |
| `<green>` | Green text |
| `<aqua>` | Aqua/cyan text |
| `<gold>` | Gold/orange text |
| `<gray>` | Gray text |
| `<yellow>` | Yellow text |
| `<light_purple>` | Light purple text |
| `<dark_purple>` | Dark purple text |
| `<bold>` | **Bold text** |
| `<italic>` | *Italic text* |
| `<gradient:red:blue>` | Gradient from red to blue |
| `<#ff5555>` | Custom hex color |
| `<click:run_command:'/cmd'>` | Clickable text |
| `<hover:show_text:'tip'>` | Hover tooltip |

## Placeholders

| Placeholder | Available In | Replaced With |
|-------------|-------------|---------------|
| `<target>` | TPA messages | Target player name |
| `<sender>` | TPA messages | Sending player name |
| `<name>` | Warp messages | Warp name |
| `<warps>` | Warp list messages | Comma-separated warp names |
| `<count>` | Player warp / listwarps messages | Current warp count |
| `<limit>` | Player warp messages | Warp limit (number or "unlimited") |
| `<player>` | Player warp / listwarps messages | Another player's name |
| `<seconds>` | Cooldowns, countdowns, delays | Remaining or countdown seconds |
| `<version>` | Version info | Plugin version string |

## All Message Keys

### General
- `prefix` — Prepended to every message. Set to `""` to disable.
- `no-permission` — Shown when lacking a permission.
- `player-not-found` — Target player not online.
- `console-not-allowed` — Player-only command used from console.
- `feature-disabled` — Shown when a feature toggle is set to `false` in config.
- `invalid-coords` — Shown when `/tp` is called with non-numeric coordinate arguments.

### Admin Warps
- `warp-created` — After `/setwarp`. Placeholder: `<name>`
- `warp-deleted` — After `/delwarp`. Placeholder: `<name>`
- `warp-not-found` — Warp doesn't exist. Placeholder: `<name>`
- `warp-list-header` — `/warps` output. Placeholder: `<warps>`
- `warp-teleporting` — Before teleporting. Placeholder: `<name>`

### Player Warps
- `pwarp-created` — After creating. Placeholders: `<name>`, `<count>`, `<limit>`
- `pwarp-deleted` — After deleting. Placeholder: `<name>`
- `pwarp-not-found` — Warp doesn't exist. Placeholder: `<name>`
- `pwarp-limit-reached` — At warp cap. Placeholder: `<limit>`
- `pwarp-no-permission` — No `telehop.warps.*` permission.
- `pwarp-list-header` — List output. Placeholders: `<warps>`, `<count>`, `<limit>`
- `pwarp-list-empty` — No warps yet.
- `pwarp-teleporting` — Before teleporting. Placeholder: `<name>`
- `pwarp-made-public` — Toggled to public. Placeholder: `<name>`
- `pwarp-made-private` — Toggled to private. Placeholder: `<name>`
- `pwarp-not-yours` — Other player's warp is private. Placeholder: `<name>`
- `pwarp-player-not-found` — Player lookup failed. Placeholder: `<player>`

### TPA
- `request-sent` — After `/tpa`. Placeholder: `<target>`
- `request-received` — Incoming `/tpa`. Placeholder: `<sender>`
- `request-received-here` — Incoming `/tpahere`. Placeholder: `<sender>`
- `request-actions` — Clickable accept/deny buttons.
- `request-accepted`, `request-denied`, `request-expired`, `request-cancelled`
- `request-none-pending`, `request-none-outgoing`
- `tpa-cooldown` — TPA on cooldown. Placeholder: `<seconds>` (remaining)
- `tpa-warmup` — Countdown message after accepting TPA. Placeholder: `<seconds>`
- `tpa-warmup-cancelled` — TPA cancelled because the player moved during warmup.

### Countdown
- `countdown-actionbar` — Action bar text during warmup countdown (shown once per second). Placeholder: `<seconds>`
- `countdown-chat` — Chat message each second during countdown. Placeholder: `<seconds>`

### Spawn
- `spawn-sent` — After `/spawn`.

### RTP
- `rtp-opening` — GUI opened.
- `rtp-searching` — Looking for safe spot.
- `rtp-teleporting` — About to teleport.
- `rtp-failed` — No safe spot found.
- `rtp-cooldown` — RTP on cooldown. Placeholder: `<seconds>` (remaining)
- `rtp-delay` — Countdown before teleporting. Placeholder: `<seconds>`
- `rtp-cancelled` — Teleport cancelled (player moved during countdown).

### Listwarps
- `listwarps-none` — No player warps found (global view).
- `listwarps-header` — Header for global warp listing. Placeholder: `<count>`
- `listwarps-player-none` — Target player has no warps. Placeholder: `<player>`
- `listwarps-player-header` — Header for a specific player's warps. Placeholders: `<player>`, `<count>`

### Admin
- `reload-success` — After `/telehop reload`.
- `forcedelwarp-admin-deleted` — After force-deleting an admin warp. Placeholder: `<name>`
- `forcedelwarp-deleted` — After force-deleting a player's warp. Placeholders: `<name>`, `<player>`

### Version
- `version-info` — Shown by `/telehop version`. Placeholder: `<version>`

### Help (`/telehop help`)

All `help-*` keys control the output of `/telehop help`. They use MiniMessage format and are displayed without the global prefix. Keys:

`help-header`, `help-general`, `help-spawn`, `help-rtp`, `help-tpa-header`, `help-tpa`, `help-tpahere`, `help-tpaaccept`, `help-tpadeny`, `help-tpacancel`, `help-warps-header`, `help-warp`, `help-setwarp`, `help-delwarp`, `help-warps-list`, `help-pwarps-header`, `help-pwarp-set`, `help-pwarp-del`, `help-pwarp-list`, `help-pwarp-tp`, `help-pwarp-tp-other`, `help-pwarp-public`, `help-admin-header`, `help-tp`, `help-tphere`, `help-listwarps`, `help-forcedelwarp`, `help-forcedelwarp-player`, `help-telehop-reload`, `help-telehop-version`, `help-telehop-perms`

### Permissions (`/telehop perms`)

All `perms-*` keys control the output of `/telehop perms`. Same display rules as help. Keys:

`perms-header`, `perms-player-header`, `perms-spawn`, `perms-rtp`, `perms-warp`, `perms-pwarp`, `perms-tpa`, `perms-tpahere`, `perms-tpa-accept`, `perms-tpa-deny`, `perms-tpa-cancel`, `perms-per-warp-header`, `perms-warp-access`, `perms-warp-limits-header`, `perms-warps-number`, `perms-warps-unlimited`, `perms-admin-header`, `perms-admin`, `perms-tp`, `perms-tphere`, `perms-bypass-header`, `perms-rtp-bypass`, `perms-rtp-bypass-delay`, `perms-tpa-bypass`

## Example: Custom Purple Theme

```yaml
prefix: "<dark_purple>[<light_purple>TP</light_purple>]</dark_purple> "
pwarp-created: "<light_purple>Warp <gold><name></gold> saved! <gray>(<count>/<limit>)"
pwarp-teleporting: "<light_purple>Warping to <gold><name></gold>..."
```
