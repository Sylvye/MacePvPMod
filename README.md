# MacePvPMod

MacePvPMod is a client-only Fabric HUD and quality-of-life mod for Minecraft **26.2**, designed for mace PvP, elytra combat, and survival awareness. It provides configurable visual and audio feedback without changing gameplay mechanics, flight, server-side damage, targeting, or network packets.

The mod includes six modules plus the shared HUD Studio workspace:

- **Elytra Pitch Bar** — a configurable on-screen pitch reference for repeatable elytra approaches and mace dives.
- **Damage Counter** — displays accumulated fall distance and confirmed mace, spear, sword, or axe damage, with reported and estimated calculation modes.
- **Attribute Swaps** — gives visual and sound feedback when a hotbar selection changes the player’s active attack attributes.
- **Survival instincts** — warns when a totem should be moved to the offhand and displays configurable low-health and low-saturation alerts, including optional audio cues.
- **Reach Outlines** — outlines reachable faces of placeable blocks and distinguishes faces that are difficult to reach.
- **Vectors** — shows a motion-direction reticle and a configurable velocity readout.

**HUD Studio** is the shared editor for overlay placement, size, colors, and previews.

Created by **Sylvye**. Source code: [github.com/Sylvye/MacePvPMod](https://github.com/Sylvye/MacePvPMod).

## Install

1. Install Minecraft 26.2 with Fabric Loader 0.19.3 or newer and Java 25.
2. Put `macepvpmod-1.0.0.jar` and **Fabric API 0.158.0+26.2** in your instance's `mods` folder.
3. Optionally install **Mod Menu 20.0.1** for the configuration button.

The mod is client-only

## Settings

Open **Mods → MacePvPMod → Configure**, or assign **Open MacePvPMod settings** under **Options → Controls → Key Binds → MacePvPMod**. The shortcut starts unbound.

Select a module from the directory. Each module has its own settings page and configuration file; HUD Studio edits the shared overlay styles.

### HUD Studio

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

- **Fall distance:** appears above the configurable **Fall threshold (blocks)**, set to 1.5 by default, at 14 GUI pixels below the crosshair. Shows Minecraft's damage-relevant fall distance. Slow elytra descents are capped at 1 block by vanilla and remain hidden at the default threshold. Vanilla fall-ending conditions and server teleports or position corrections reset the counter.
- **Weapon damage:** independently enable mace (default on), spear (default on), and sword & axe (default off). Choose **Damage: Reported** (default) or **Damage: Calculated**. Both show damage points (2 points = 1 heart) after a server-confirmed hit on a living entity, for 3 seconds by default.
- Each feature has its own enable toggle and message template. Hit duration is configurable from 1–10 seconds. Appearance is edited in **HUD Studio**.
- Fall defaults to `{blocks} blocks`; hit defaults to `{damage} damage`. Hit messages also support `{blocks}` for the same Minecraft fall distance captured at attack time. Values use one decimal place. For example, `{damage} damage from {blocks} blocks` becomes `18.0 damage from 12.5 blocks`.
- A non-gliding mace smash is identified at attack time when fall distance is strictly above 1.5 blocks. After the matching server damage event confirms it, the local fall-distance accumulator is reset. The hit message still uses the attack-time snapshot, so the reset does not change its `{blocks}` value. Spear, sword, axe, and elytra-gliding attacks do not trigger this reset.
- Variable insertion buttons, explanations, and live examples appear beside the fields. Blank messages and unsupported variables block saving. Each new hit replaces the previous hit message.
- **Save** applies changes; **Cancel** or Escape discards them. Damage settings persist separately in `config/macepvpmod-damage.json`.

**Reported** uses server health updates. A confirmed hit without a measurable health decrease displays **Damage unavailable**. Absorption damage is not included; overlapping damage from other sources may affect observed health loss.

**Calculated** works without target health updates. It snapshots the weapon and relevant attack state, includes weapon attributes, cooldown, critical hits, and applicable damage enchantments, and predicts spear charges from client-visible movement and targeting data. Spear prediction is read-only and never invokes combat actions or sends packets. Charge damage uses the player's base attack damage plus the relative-velocity bonus; unlike a jab, it does not include the spear's material attack modifier or Strength and Weakness modifiers. Both modes still require a server damage-event confirmation; calculated mode does not treat unconfirmed swings as successful hits.

Strength, Weakness, custom attack attributes, and attribute swaps are included in attack snapshots. Reported mode prefers observed health loss, but uses the saved calculation when health synchronization, invulnerability behavior, or a totem activation prevents a reliable positive health delta.

**Bold critical damage** is enabled by default. It bolds damage only after a server-confirmed ordinary attack met every vanilla critical-hit condition at attack time. Falling alone is insufficient, and spear jabs and charges are never styled as critical hits.

**Use enemy gear** is available only in calculated mode. It estimates post-gear health damage using client-visible armor, toughness, Protection, and Breach. It intentionally excludes Resistance, absorption, active shields, server plugins, and equipment hidden from the client, so the result is a best-effort estimate.

The Minecraft 26.2 formula, verified against the bundled `MaceItem` and `Player` implementations and `data/minecraft/enchantment/density.json`, is:

- Base: attack damage attribute (normally 6 for a mace) × `(0.2 + 0.8 × cooldown²)`.
- Smash bonus, only when fall distance `f > 1.5` and not elytra-gliding: `4 × min(f, 3) + 2 × min(max(f − 3, 0), 5) + max(f − 8, 0)`.
- Density adds `0.5 × level × f` to a smash. Without Density, including a Breach mace, this addition is zero.
- A valid critical hit at cooldown above 0.9 multiplies the combined base and smash damage by 1.5, matching this version's attack code. Cooldown does not scale the smash bonus.

Existing configurations retain reported mode. Non-living targets are not tracked.

### Vectors

Vectors has a module **Enabled** switch and two independent outputs:

- **Motion reticle:** **Show reticle** controls it. **When gliding** allows it while the player is actively gliding, and **When charging** allows it while the player is holding right-click to charge a spear attack. When both filters are enabled, either condition is sufficient; when both are disabled, the reticle is unrestricted. The reticle is hidden below **Stationary threshold** speed, measured in blocks/second. It points toward the player’s current velocity relative to the camera and is clamped inside the screen using the configured **Size** margin.
- **Velocity readout:** **Show velocity** controls it. **Display threshold** hides it at or below the configured speed and defaults to 0.0, so it disappears while stationary. It remains independent of the reticle’s activity filters and stationary threshold. **Message** must contain the only supported variable, `{magnitude}`; the value is formatted to one decimal place. The readout uses a configurable **Velocity colors** scale and is placed and scaled through **HUD Studio → Velocity**.

The Vectors module is disabled by default. Reticle settings are **Icon** (`Circle`, `Crosshair`, or `Star`), **Size** (3–31 GUI pixels), **Opacity** (5–100%), **Reticle color**, and the stationary threshold (0–20 blocks/second). The default is Circle, size 7, white, 90% opacity, and a 0.05 blocks/second threshold. Both activity filters are enabled by default, allowing the reticle while gliding or charging a spear. The default velocity message is `{magnitude} blocks/s`.

The displayed magnitude is the length of the effective client movement vector multiplied by 20, in blocks/second. While grounded, vertical movement is ignored; while airborne, vertical movement contributes to both the magnitude and direction. The default velocity color scale is a gradient over 0–40 blocks/second: `#55FF88` at the low end, `#FFFF55` at the midpoint, and `#FF5555` at the high end. The color editor supports Flat or Gradient mode, a flat color, domain minimum/maximum, and ordered gradient keys with editable colors and intermediate positions.

Vectors is hidden while no world/player is loaded, a menu is open, the HUD is hidden (F1), the player is dead, or the player is spectating. Settings are stored in `config/macepvpmod-vectors.json`. Version 1 files migrate to version 2 by retaining supported values, enabling both activity filters, and removing the old radius field; invalid files fall back to defaults and are backed up.

### Attribute Swaps

Detects an attribute-changing hotbar swap during combat and optionally shows an **Attribute swap!** HUD message for three seconds and plays a configurable sound. By default, it registers only after a successful hit and only when swapping to a weapon; both filters can be disabled. Visual and sound feedback can be controlled separately. The default sound is `minecraft:entity.experience_orb.pickup`. Move, resize, and color the text through **HUD Studio → Attribute swap** or **Edit in HUD**. It appears in individual and global previews and does not use the actionbar.

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
