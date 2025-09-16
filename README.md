# Yukari - A NeoForge Mod for Mixed Mod Arts Fighting

## Usage

### For Admin:

- Use `/combatstart` to start the match. Players who have selected a mod are randomly teleported far from spawn and
  switched to Survival; others stay Spectator. The final phase will start automatically after the configured delay.
- Use `/combatrun` to reset the game. It clears player selections and inventories, restores world border and spawn,
  teleports everyone back, and writes a world-reset marker so regions are wiped (after backup) on the next server start.
- Use `/combattime` to view the remaining time until the final phase or the current match state.
- Use `/combattp <player>` as a Spectator to teleport to a specific participant who is in Survival.

### For Player:

- On first join, select a mod identity via the in‑game GUI (or clickable chat if enabled). Until you select, you are
  Spectator.
- Before the final phase, you may only pick up/craft/use items from the `minecraft` namespace or your chosen mod’s
  namespace. Other items will be blocked and periodically purged from your inventory with a warning.
- Use `/combatin` to open the selection UI and join the ongoing match (only before the final phase).
- Use `/yukari_select <mod>` to select via chat command (used by the clickable chat list). Use `/yukari_spectate` to
  become Spectator.
- When the final phase starts, everyone is teleported to the world center and the world border shrinks; from then on,
  all items are allowed. Death during the final phase sets you to Spectator.

## Notes

- Dynamic mod list: the server’s installed mods are auto‑detected (excluding `minecraft`, `neoforge`, and `yukari`). If
  none are found, the fallback list `classicMods` in the config is used.
- World reset: `/combatrun` creates a `.yukari_world_reset` marker. On the next startup, the server backs up and wipes
  `region`/`poi`/`entities` under the world root so new terrain generates.
- Random teleports: players are placed using a ring distribution between `minRandomDistance` and `randomTeleportRange`,
  searching for safe ground; a square‑area fallback is used if needed.
- Timing: the final phase delay is controlled by `finalPhaseDelayTicks` (default 72,000 ticks ≈ 1 hour). Periodic
  countdown broadcasts are sent, plus a 1‑minute warning.
- Restrictions: warning messages for disallowed items are throttled by `restrictionMessageCooldownSec`.
- Command permissions: `/combatstart` and `/combatrun` require permission level 2; player commands are available to
  everyone.
- Configuration (NeoForge `yukari-common.toml`):
    - `allowMultipleSameMod` (default true)
    - `useChatSelection` (default false)
    - `classicMods` (string list)
    - `randomTeleportRange`, `minRandomDistance`, `safeSpotMaxTries`
    - `finalPhaseDelayTicks`, `restrictionMessageCooldownSec`
- Platform: built for NeoForge on modern Minecraft (1.21.1). A client‑side selection GUI is provided; if unavailable,
  clickable chat selection is used.
