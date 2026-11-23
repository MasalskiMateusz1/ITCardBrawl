# Design reference — raw values

Source: Claude Design canvas "ITCardBrawl — Card frame, Battle board, Main menu"
(https://claude.ai/artifact/UDTNmTGxfcCzaZ4v1yyd5t, canvas version `1795355348-c7d2`, read 2026-11-22),
artboards `project/Board.dc.html` (B:) and `project/Card.dc.html` (C:). Values copied verbatim; role
names are this change's proposal for the token layer in `frontend/src/styles.css`.

## Colours

| Proposed token | Value | Role in the design | Source |
| --- | --- | --- | --- |
| `--color-bg` | `#0B0D11` | page ground | B:18 |
| `--color-overlay` | `rgba(11,13,17,0.97)` | graveyard / log overlay surface | B:114, B:139 |
| `--color-panel` | `#12161C` | aside panels, exit link | B:156, 181, 185, 204 |
| `--color-surface` | `#161B22` | card body, list rows, stat badge ring | C:16; B:126, 148 |
| `--color-raised` | `#1A1F27` | pills, secondary buttons | B:56, 61, 67, 119, 142, 176, 200 |
| `--color-raised-2` | `#1E242E` | avatar tile | B:158, 187 |
| `--color-border` | `#2A313C` | default border, empty tracks, chips | B:21, 56, 64, 165, 173, 321; C:29, 39 |
| `--color-border-strong` | `#3A424E` | Log button, log overlay frame | B:67, 139 |
| `--color-text` | `#E6EDF3` | primary text; selected ring | B:18; C:16, 91 |
| `--color-text-secondary` | `#C9D1D9` | section labels, last-move line | B:27, 62, 66, 167 |
| `--color-text-muted` | `#9BA7B4` | muted labels, flavor, "brak" | C:34, 39; B:337 |
| `--color-on-accent` | `#0E1116` | text on accent (cost circle, End turn) | C:18; B:69 |
| `--color-attack` / `--color-on-attack` | `#FFD166` / `#1A1300` | attack badge | C:25, 53 |
| `--color-danger` / `--color-on-danger` | `#E5484D` / `#FFFFFF` | HP badge, HP bar | C:26; B:165, 192 |
| `--color-slot-border` | `rgba(230,237,243,0.14)` | empty slot dashed border | B:35, 49, 86, 100 |
| `--color-slot-fill` | `rgba(14,17,22,0.35)` | empty slot fill | same |
| `--color-divider` | `rgba(230,237,243,0.18)` | characters/events divider | B:39, 90 |
| `--color-link` / hover | `#9CC4FF` / `#C8DDFF` | link colours | B:15 |
| `--shadow-card` | `0 8px 20px rgba(0,0,0,0.45)` | card default | C:91 |
| `--shadow-card-selected` | `0 0 0 3px #E6EDF3, 0 12px 28px rgba(0,0,0,0.55)` | card selected | C:91 |

Faction set (C:65-69):

| Faction | accent | accent-soft | tint |
| --- | --- | --- | --- |
| developers | `#4C9AFF` | `rgba(76,154,255,0.45)` | `#0F1E33` |
| testers | `#FF9F43` | `rgba(255,159,67,0.45)` | `#2A1A0B` |
| neutral | `#A9B1BD` | `rgba(169,177,189,0.45)` | `#1C2129` |

Not-playable card: `opacity: 0.6; filter: saturate(0.3)` (C:92-93).

## Typography

- Body: Inter 400/500/600 — `Inter, system-ui, sans-serif` (B:18).
- Display (card names, primary button, names, overlay titles): Space Grotesk 500/600/700.
- Numbers (cost, attack, HP, timer, budget): JetBrains Mono 400/600/700.
- Section label: 12px / 600 / `letter-spacing: 0.08em` / uppercase (B:27).
- Card name full 15/700 lh 1.15 (C:19), compact 12.5/700 (C:46); primary button 18/700 Space Grotesk (B:69).

## Radii

16 zone/panel/overlay · 14 full card · 12 compact card/slot/button · 10 avatar/log row · 8 art window
· 20 pill · 50% badges.

## Spacing

Page padding 16 gap 16; column gap 12; zone padding `14px 20px 16px` gap 20; label→row 8; board cards
gap 12; hand gap 14; panel padding `12px 14px` gap 8; card padding 8 (full) / 6 (compact).

## Fixed sizes

Frame 1440×900 (no other width drawn); aside 280; zones 270 tall; turn bar 56; full card 240×336;
compact card 150×210; pills 40; Log / End-turn buttons 48; graveyard/close/exit 44; timer bar 64×6; HP
bar 8; avatar 40.
