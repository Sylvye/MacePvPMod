# MacePvPMod

MacePvPMod is a client-only Fabric HUD and quality-of-life mod for Minecraft **26.2**, designed for mace PvP, elytra combat, and survival awareness. It provides configurable visual and audio feedback without changing gameplay mechanics, flight, server-side damage, targeting, or network packets.

The mod includes five modules:

- **HUD** — central editor for overlay placement, size, colors, and previews.
- **Elytra Pitch Bar** — a configurable on-screen pitch reference for repeatable elytra approaches and mace dives.
- **Damage Counter** — displays accumulated fall distance and confirmed mace-hit damage, with reported and estimated calculation modes.
- **Attribute Swaps** — gives visual and sound feedback when a hotbar selection changes the player’s active attack attributes.
- **Survival instincts** — warns when a totem should be moved to the offhand and displays configurable low-health and low-saturation alerts, including optional audio cues.

Created by **Sylvye**. Source code: [github.com/Sylvye/MacePvPMod](https://github.com/Sylvye/MacePvPMod).

## Install

1. Install Minecraft 26.2 with Fabric Loader 0.19.3 or newer and Java 25.
2. Put `macepvpmod-1.0.0.jar` and **Fabric API 0.158.0+26.2** in your instance's `mods` folder.
3. Optionally install **Mod Menu 20.0.1** for the configuration button.

The mod is client-only

## Settings

Open **Mods → MacePvPMod → Configure**, or assign **Open MacePvPMod settings** under **Options → Controls → Key Binds → MacePvPMod**. The shortcut starts unbound.

Select a module from the directory. Each has a separate settings page and configuration file.

### HUD

Select an element, then choose **Drag / resize preview**. Drag the text or bar to move it, drag its bottom-right handle to resize it, or use arrow keys to nudge it. The controls page also provides numeric offsets and size, per-element reset, and color pickers with hue, saturation/brightness, presets, and optional hex entry. The pitch bar has width, thickness, and opacity controls.

Switch between **all elements** and **selected only** previews. Samples remain visible even when the corresponding module is disabled. The healing warning has separate low-health, low-saturation, and combined colors. Preview edits apply only after **Save**; **Cancel** discards them.

HUD appearance is stored in `config/macepvpmod-hud.json`. Until this file exists, legacy module appearance settings are imported automatically. Existing module files remain intact. Saved HUD appearance takes precedence over their old appearance fields.

### Elytra Pitch Bar

- **Basic:** enabled and a shortcut to the HUD editor.
- **Advanced:** target pitch, pixels per degree, maximum vertical travel, and third-person visibility.
- **Save** applies and persists edits. **Cancel** or Escape discards them. **Reset behavior defaults** resets both pages; Save applies the reset.

Defaults: 100 GUI-pixel width, 1-pixel thickness, `999999` grey, 40% opacity, +40° target, 2 GUI pixels per degree, ±60 GUI pixels of travel.

Settings are stored in `config/macepvpmod.json` in the game instance. Changes made externally load at startup. Missing fields use defaults; numeric values are bounded. Invalid configuration is copied to a uniquely named `macepvpmod-invalid-*.json` backup and defaults are used. Saving replaces the file atomically; errors leave active settings intact and keep the settings screen open.

### Damage Counter

- **Fall distance:** appears above the configurable **Fall threshold (blocks)**, set to 1.5 by default, at 14 GUI pixels below the crosshair. Measures actual downward movement, including slow elytra descents. Ascending, landing, entering water/lava, climbing, riding, creative flight, respawning, or server position corrections reset the counter. Level flight preserves the current descent; toggling elytra alone does not reset it.
- **Mace hit damage:** choose **Damage: Reported** (default) or **Damage: Calculated**. Both show damage points (2 points = 1 heart) after a server-confirmed mace hit on a living entity, for 3 seconds by default.
- Each feature has its own enable toggle and message template. Hit duration is configurable from 1–10 seconds. Appearance is edited in **HUD**.
- Fall defaults to `{blocks} blocks`; hit defaults to `{damage} damage`. Hit messages also support `{blocks}` for Minecraft’s damage-related fall distance captured at attack time. This can be smaller than the physical descent shown by the fall counter during slow elytra flight. Values use one decimal place. For example, `{damage} damage from {blocks} blocks` becomes `18.0 damage from 12.5 blocks`.
- Variable insertion buttons, explanations, and live examples appear beside the fields. Blank messages and unsupported variables block saving. Each new hit replaces the previous hit message.
- **Save** applies changes; **Cancel** or Escape discards them. Damage settings persist separately in `config/macepvpmod-damage.json`.

**Reported** uses server health updates. A confirmed hit without a measurable health decrease displays **Damage unavailable**. Absorption damage is not included; overlapping damage from other sources may affect observed health loss.

**Calculated** works without target health updates. The message has no added mode suffix. It snapshots the mace, fall distance, attack attribute, cooldown, and critical-hit conditions when attacking. It estimates raw outgoing damage before armor, toughness, Protection, Resistance, absorption, shields, or server modifications; Breach armor piercing is ignored. Target-specific enchantment bonuses such as Smite are not calculated. Both modes still require a server damage-event confirmation; calculated mode does not treat unconfirmed swings as successful hits.

The Minecraft 26.2 formula, verified against the bundled `MaceItem` and `Player` implementations and `data/minecraft/enchantment/density.json`, is:

- Base: attack damage attribute (normally 6 for a mace) × `(0.2 + 0.8 × cooldown²)`.
- Smash bonus, only when fall distance `f > 1.5` and not elytra-gliding: `4 × min(f, 3) + 2 × min(max(f − 3, 0), 5) + max(f − 8, 0)`.
- Density adds `0.5 × level × f` to a smash. Without Density, including a Breach mace, this addition is zero.
- A valid critical hit at cooldown above 0.9 multiplies the combined base and smash damage by 1.5, matching this version's attack code. Cooldown does not scale the smash bonus.

Existing configurations retain reported mode. Non-living targets are not tracked.

### Attribute Swaps

Detects an attribute-changing hotbar swap during combat and optionally shows an **Attribute swap!** HUD message for three seconds and plays a configurable sound. By default, it registers only after a successful hit and only when swapping to a weapon; both filters can be disabled. Visual and sound feedback can be controlled separately. The default sound is `minecraft:entity.experience_orb.pickup`. Move, resize, and color the text through **HUD → Attribute swap** or **Edit in HUD**. It appears in individual and global previews and does not use the actionbar.

### Survival instincts

- **Totem warning:** displays a configurable alert when the player has a Totem of Undying in the inventory but the offhand is empty.
- **Health and saturation warnings:** displays separate configurable messages for low health, low saturation, or both at once.
- **Audio cues:** an ordered playlist plays one entry per warning beat and loops. Add, remove, reorder, search, and preview registered sounds; each entry has volume and pitch controls. An empty playlist mutes warnings. Missing sound events are skipped and remain editable.
- Low health increases volume and shortens the gap between beats. Existing harp/bass settings migrate into two playlist entries with their original volume and pitch.
- Text, thresholds, and timing remain here; colors, size, and placement live in **HUD**. Playlist **Done** returns a draft; save Survival instincts to apply it.

These alerts are hidden while viewing menus, spectating, dead, paused, or hiding the HUD. Configurations are stored in `config/macepvpmod-attribute-swaps.json` and `config/macepvpmod-survival.json`.

## Build and test

Requires JDK 25; Gradle is provided by the wrapper.

```sh
./gradlew build
./gradlew runClient
./gradlew runClientGameTest
./gradlew runClientGameTest -PwithoutModMenu
```

Windows: use `gradlew.bat`. The installable mod and source JAR are in `build/libs/`. Unit test reports are in `build/reports/tests/test/`; in-game screenshots are in `build/run/clientGameTest/screenshots/`. Game tests use an isolated disposable instance under `build/run/`; they do not touch your normal Minecraft saves or configuration.

Pinned toolchain: Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Loom 1.17.20, Gradle 9.5.1, Mod Menu 20.0.1. Rendering uses Fabric's HUD API and Minecraft GUI drawing rather than backend-specific OpenGL calls.

## Code structure

`dev.macepvpmod` contains the client entrypoint, independent pitch math and immutable settings, configuration persistence, HUD renderer, native settings screen, and optional Mod Menu integration. The mod identifier and asset namespace are `macepvpmod`; the displayed name is **MacePvPMod**. Future informational features can have separate renderers and settings sections.
