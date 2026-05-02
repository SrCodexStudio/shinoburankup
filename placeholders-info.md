# ShinobuRankup - Complete Placeholders Reference

> All placeholders listed here are verified and functional in v3.0.1.
> MiniMessage tags (`<red>`, `<gradient:...>`, `<bold>`) are supported everywhere.

---

## PlaceholderAPI Placeholders (%shinobu_...%)

Use these in any plugin that supports PlaceholderAPI (TAB, Scoreboard, DeluxeChat, HolographicDisplays, etc.)

### Current Rank

| Placeholder | Description | Example Output |
|---|---|---|
| `%shinobu_rank%` | Current rank display name with colors | `&7[&f★ 5&7]` |
| `%shinobu_rank_id%` | Current rank internal ID | `star_5` |
| `%shinobu_rank_prefix%` | Current rank chat prefix | `&7[★5] ` |
| `%shinobu_prefix%` | Shorthand for rank_prefix | `&7[★5] ` |
| `%shinobu_rank_plain%` | Rank name without colors | `[★ 5]` |
| `%shinobu_rank_order%` | Rank position number | `5` |
| `%shinobu_rank_cost%` | Current rank cost (formatted) | `$20,000.00` |

### Next Rank

| Placeholder | Description | Example Output |
|---|---|---|
| `%shinobu_next%` | Next rank display name with colors | `&7[&f★ 6&7]` |
| `%shinobu_next_rank%` | Same as above (alias) | `&7[&f★ 6&7]` |
| `%shinobu_next_rank_id%` | Next rank ID (or "MAX") | `star_6` |
| `%shinobu_next_rank_plain%` | Next rank name without colors | `[★ 6]` |
| `%shinobu_next_cost%` | Next rank cost (formatted) | `$30,000.00` |
| `%shinobu_next_cost_raw%` | Next rank cost (raw number) | `30000.0` |

### Money Progress

| Placeholder | Description | Example Output |
|---|---|---|
| `%shinobu_progress%` | Progress percentage toward next rank | `64.50` |
| `%shinobu_progress_bar%` | Visual progress bar (10 chars) | `██████░░░░` |
| `%shinobu_progress_bar_20%` | Custom length bar (1-50) | `████████████░░░░░░░░` |
| `%shinobu_balance%` | Player's current balance (formatted) | `$19,350.00` |
| `%shinobu_money_needed%` | Money still needed (formatted) | `$10,650.00` |
| `%shinobu_money_needed_raw%` | Money needed (raw number) | `10650.0` |
| `%shinobu_can_rankup%` | Can afford next rank? | `Yes` or `No` |

### Playtime Progress

| Placeholder | Description | Example Output |
|---|---|---|
| `%shinobu_playtime%` | Current playtime | `5h 30m` |
| `%shinobu_playtime_required%` | Playtime needed for next rank | `12h 0m` |
| `%shinobu_playtime_progress%` | Playtime progress percentage | `45.83` |
| `%shinobu_playtime_progress_bar%` | Playtime visual bar (10 chars) | `████░░░░░░` |

### Player Stats

| Placeholder | Description | Example Output |
|---|---|---|
| `%shinobu_total_spent%` | Total money spent (formatted) | `$150,000.00` |
| `%shinobu_total_spent_raw%` | Total money spent (raw) | `150000.0` |
| `%shinobu_rankup_count%` | Number of rankups completed | `12` |
| `%shinobu_position%` | Leaderboard position | `3` or `N/A` |
| `%shinobu_is_max_rank%` | At maximum rank? | `Yes` or `No` |

---

## GUI Lore Placeholders ({...})

Use these inside `lore:` fields in `ranks.yml` and in `lore-templates:` in `gui/ranks.yml`.

### Single-Line (replaced inline)

| Placeholder | Description | Example Output |
|---|---|---|
| `{rank_display}` | Rank display name with colors | `&7[&f★ 5&7]` |
| `{rank_id}` | Rank internal ID | `star_5` |
| `{prefix}` | Rank display color code | `&7` |
| `{cost}` | Rank cost formatted | `$20,000.00` |
| `{cost_raw}` | Rank cost raw number | `20000.0` |
| `{order}` | Rank order number | `5` |
| `{status}` | Status text with color | `&eCurrent` |
| `{status_color}` | Status color code | `&a`, `&e`, `&b`, or `&c` |
| `{balance}` | Player's current balance | `$15,000.00` |
| `{remaining}` | Money still needed | `$5,000.00` |
| `{progress}` | Money progress percentage | `75.0` |
| `{progress_bar}` | Money progress bar (10 chars) | `&a███████&7░░░` |
| `{player}` | Player name | `Steve` |
| `{action}` | Click hint based on status | `&aClick to rank up` |
| `{playtime}` | Player's current playtime | `5h 30m` |
| `{playtime_required}` | Required playtime for rank | `12h 0m` |
| `{playtime_progress}` | Playtime progress % | `45.8` |
| `{playtime_progress_bar}` | Playtime progress bar | `&e████&7░░░░░░` |

### Multi-Line (expand to 0+ lines when alone on a line, removed if empty)

| Placeholder | Description | Expands To |
|---|---|---|
| `{description}` | Rank description from ranks.yml | Each line from the `description:` list |
| `{requirements}` | Requirements with checkmarks | `Requirements:` header + `✓`/`✗` per item |
| `{rewards}` | Rewards preview | `Rewards:` header + bullet per reward |

### Example Usage in ranks.yml

```yaml
star_5:
  lore:
    - ""
    - "&#f9a23e✰ Requirements:"
    - " &#FBCC23🏴 &fTime Played &#946FEC10 Hours ⌚"
    - " &#FBCC23🏴 &fMoney Needed &#feb801$20M 💰"
    - ""
    - "&#FFDF20☃ Progress:"
    - " {progress_bar} {status_color}{progress}%"
    - ""
    - "&#FFDF20🕐 Time Required: &7{playtime_required}"
    - " {playtime_progress_bar} &e{playtime_progress}%"
    - ""
    - "&#5BFB1A✔ Rewards:"
    - " &#05f800🧪 &fMultiplier: &#05f800x0.2"
    - ""
    - "&#f9ee29⮕ &lCLICK&r &#f9ee29to rank up"
```

---

## Message Placeholders ({...})

Use these in `lang/en.yml` and `lang/es.yml` message strings.

### Rankup Success Message (rankup.success.message)

| Placeholder | Description |
|---|---|
| `{rank}` | New rank display name |
| `{previous_rank}` | Previous rank display name |
| `{cost}` | Cost paid (formatted) |
| `{next_rank}` | Next rank after the new one (or "MAX") |

### Title / Subtitle / ActionBar

| Placeholder | Description |
|---|---|
| `{player}` | Player name |
| `{rank}` | New rank display name |
| `{rank_display}` | Same as {rank} |
| `{rank_id}` | New rank internal ID |
| `{next_rank}` | Next rank display name |

### Broadcast Message

| Placeholder | Description |
|---|---|
| `{player}` | Player name |
| `{rank}` | New rank display name |
| `{rank_display}` | Same as {rank} |
| `{rank_id}` | New rank internal ID |
| `{tier}` | Rank tier number |
| `{next_rank}` | Next rank display name |

### Max Rankup Message

| Placeholder | Description |
|---|---|
| `{player}` | Player name |
| `{count}` | Number of ranks gained |
| `{old_rank}` | Previous rank display name |
| `{rank}` | Final rank display name |
| `{rank_display}` | Same as {rank} |
| `{total_cost}` | Total money spent (raw) |
| `{total_cost_formatted}` | Total money spent (formatted) |
| `{balance}` | Remaining balance (raw) |
| `{balance_formatted}` | Remaining balance (formatted) |

### Commands in ranks.yml

| Placeholder | Description |
|---|---|
| `{player}` or `%player%` | Player name |
| `{player_uuid}` or `%player_uuid%` | Player UUID |
| `{rank}` or `%rank%` | Rank display name |
| `{rank_display}` or `%rank_display%` | Same as {rank} |

### Error / Failure Messages

| Placeholder | Context | Description |
|---|---|---|
| `{cost}` | fail-no-money | Required cost |
| `{balance}` | fail-no-money | Current balance |
| `{remaining}` | cooldown | Seconds remaining |
| `{current}` | requirement-* | Player's current value |
| `{required}` | requirement-* | Required value |
| `{permission}` | requirement-permission | Permission node name |
| `{item}` | requirement-items | Item material name |

---

## Format Support Summary

| Context | `&c` codes | `&#RRGGBB` hex | MiniMessage tags | Unicode emoji |
|---|---|---|---|---|
| Chat messages | ✅ | ✅ | ✅ | ✅ |
| GUI item lore | ✅ | ✅ | ✅ | ✅ |
| GUI item names | ✅ | ✅ | ✅ | ✅ |
| ranks.yml lore | ✅ | ✅ | ✅ | ✅ |
| gui/ranks.yml templates | ✅ | ✅ | ✅ | ✅ |
| Title / Subtitle | ✅ | ✅ | ✅ | ✅ |
| Action bar | ✅ | ✅ | ✅ | ✅ |
| Tab list prefix | ✅ | ✅ | ✅ | ✅ |
| PlaceholderAPI output | ✅ | ✅ | ❌ (legacy only) | ✅ |

---

## Notes

- PlaceholderAPI placeholders use `%shinobu_...%` format
- GUI lore placeholders use `{...}` format (curly braces)
- Both `{player}` and `%player%` work in rank commands
- All placeholders are case-insensitive
- Multi-line placeholders (`{description}`, `{requirements}`, `{rewards}`) only work in GUI lore, not in chat messages
- `%shinobu_progress_bar_N%` supports custom bar length (1-50), only in PAPI format
- When at max rank: next_rank returns "MAX", progress returns 100%, can_rankup returns "No"
- Bedrock players (GeyserMC/Floodgate) with `.` prefix in names are fully supported
