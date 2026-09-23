# Verification — September 3, 2026

- `./gradlew build`: passed; 9 unit tests, zero failures or errors.
- `./gradlew runClient`: Minecraft 26.2 reached the title screen with SylvyesPvPHud and Mod Menu 20.0.1 loaded.
- `./gradlew runClientGameTest`: both client game tests passed with Mod Menu installed.
- `./gradlew runClientGameTest -PwithoutModMenu`: both client game tests passed without Mod Menu loaded.
- Rendered and visually inspected basic settings, advanced settings, and the 40° gliding bar. The guide aligns with the crosshair and stays beneath it.
- Client tests cover draft isolation, Cancel, Save, Reset, invalid color validation, advanced switches, gliding versus grounded/non-gliding states, F1, first/third person visibility, menu hiding, death, disconnection, and rendering at requested GUI scales 1/2/3 (Minecraft clamps unsupported scales).
- Unit tests cover signed deviation, target alignment, travel limits, custom target/sensitivity, missing configuration, persistence, invalid file backup/recovery, missing fields, invalid types/schema versions, bounds, and failed saves preserving active configuration.

Environment: macOS ARM64, Java 25.0.4.1, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2. Rendering backend: OpenGL 4.1 Metal.

Limits: Vulkan was not tested. Actual window resizing, spectator mode, the unbound shortcut after user assignment, and clicking the configuration button through Mod Menu itself were not manually exercised. Settings and optional Mod Menu integration compile against the real 26.2 APIs; configuration screens were exercised directly by the client tests. Live multiplayer combat has not been verified; damage reporting uses observed health changes, not damage predictions.

## Settings profiles — September 21, 2026

- Added a versioned, atomic profile catalog spanning all eight configuration records, with one-time import of the legacy loose files.
- Unit coverage verifies catalog creation/reload, invalid-catalog backup, exact address normalization, unique server ownership, matched/unmatched selection, connection-scoped overrides, automatic restoration, clipboard round trips, address exclusion, damaged imports, and collision-safe imported names.
- `/hudprofile`, `/hudprofile <name>`, and `/hudprofile auto` are client commands; profile activation and server matching send no packets.
- New clipboard payloads use a typed binary delta from defaults, optional raw DEFLATE, CRC32 integrity, and a 1 MiB decoded limit. Dense Unicode `SPH2:` is the shortest default; URL-safe Base64 `SPH2A:` is the compatibility fallback, and legacy gzip/Base64 `SPH1:` remains importable.
- Oversized Unicode exports are split into numbered sub-1,900-character Discord chunks. Import validates a complete, non-duplicated, single-format chunk set before decoding.
- Limitation: automated tests model connection transitions directly; live server, Realms, and operating-system clipboard interaction have not been manually exercised.


## Damage Counter update

- `./gradlew build runClientGameTest`: passed with 11 unit tests and both client game tests.
- Verified independent damage settings persistence, default migration, bounds, module navigation, separate fall/hit toggles, reset, and position preview.
- Client combat checks use a controlled entity and injected damage packets through Minecraft's real packet handler: no result before confirmation, an 8-point health decrease displays 8 damage, expiry after 60 ticks, unavailable health data, and ignoring non-mace attacks. The mixin loaded and executed successfully.
- Verified the strict fall threshold (hidden at 1.5, visible at 1.51, hidden after reset).
- Visually inspected the damage settings and both default HUD positions in the position preview.
- Limitation: these are controlled client checks, not an end-to-end multiplayer combat test. Reported damage is health lost; absorption and overkill are excluded, and overlapping damage can affect the observed delta.

## Reported / calculated damage update

- `./gradlew build runClientGameTest`: passed; 15 unit tests and both client game tests.
- Formula verified directly against Minecraft 26.2's `MaceItem.getAttackDamageBonus`, `MaceItem.canSmashAttack`, `Player.attack`, `Player.baseDamageScaleFactor`, `Player.canCriticalAttack`, and the bundled Density enchantment JSON.
- Math tests cover 1.5/3/8-block boundaries, fractional falls, Density I–V, long falls, cooldown, gliding, and critical scaling.
- Client checks verify reading Density V from a mace adds 20 raw points at 8 blocks; Breach IV leaves raw damage unchanged; calculated mode waits for hit confirmation but needs no health decrease; resetting fall distance and switching weapons after attacking does not change the saved calculation.
- Settings checks verify the new mode does not apply before Save, persists after Save, and resets to Reported. Old configuration files default to Reported.
- Visually inspected the calculated-mode settings page. Live multiplayer and custom server damage rules remain untested.

## Settings and HUD upgrade — September 4, 2026

- `./gradlew test build runClientGameTest`: passed with Mod Menu installed; 41 unit tests, zero failures/errors, and all three client game tests passed.
- An earlier full client run also passed with `-PwithoutModMenu`.
- Added regression coverage for template validation/persistence, legacy audio and HUD migration, empty/unavailable/duplicate sound entries, playlist iteration/reset, invalid styles, and damaged HUD file backup.
- Client checks cover HUD draft isolation, numeric validation, dragging, resizing, nudging, persistence, color validation, and attack-time `{blocks}` in calculated hit messages without a mode suffix.
- Captured and visually reviewed the picker, HUD controls/global preview, module directory, sound playlist, and damage template screen at requested GUI scales 1/2/3. Minecraft clamps scales unsupported by the test window.
- Existing combat confirmation, visibility, survival-item, and cadence tests continue to pass. The client runner uses disposable configurations under `build/run/clientGameTest`.
- Limits: no live multiplayer session or physical window-resize interaction was tested. Arbitrary sound content and audio-device behavior were not exhaustively auditioned.

## Attribute Swap HUD follow-up

- Attribute swap notifications now render as the sixth editable HUD element instead of using the actionbar; added the module shortcut and preserved the visual toggle.
- `./gradlew test build runClientGameTest` passed (42 unit tests and all three client tests).
- Verified old HUD-file migration preserves other styles, the new style persists, notifications expire after three seconds, and F1/menus hide them. Visually reviewed the global preview with Attribute swap selected.

## Vanilla fall counter fix — September 10, 2026

- Confirmed Minecraft 26.2 calls `Entity.checkFallDistanceAccumulation` from `LivingEntity.updateFallFlying`, capping its damage accumulator at 1 when vertical velocity exceeds -0.5 blocks/tick.
- Fall HUD now displays the local player's vanilla damage-relevant fall distance. Server teleports and position corrections explicitly reset the client accumulator.
- `./gradlew build runClientGameTest`: passed. New unit tests cover shallow descent, speed changes, level flight, ascent, and resets.
- Client regression checks that shallow elytra descent stays capped at 1 and hidden, normal falling accumulates, vanilla resets clear it, and a player-position packet clears pre-teleport distance.
- Mace calculations, the live HUD, and hit-template fall-distance snapshots all use Minecraft's damage accumulator. Live multiplayer flight has not been manually tested.

## Vectors HUD and confirmed mace-smash reset — September 13, 2026

- `./gradlew test build runClientGameTest`: passed with 78 unit tests and all three client game tests. The unit suite includes Vectors math, template validation, defaults and bounds, persistence, v1 migration, invalid-file backup, and equipment-filter rules; the client smoke test covers normal rendering, elytra/spear filter acceptance, F1/menu hiding, and the confirmed smash reset.
- Vectors defaults and behavior were checked against the implementation: grounded vertical velocity is ignored, airborne velocity contributes to magnitude and direction, magnitude is reported in blocks/second to one decimal place, the reticle uses camera-relative projection and screen-edge clamping, and the velocity readout remains independent of reticle filters and the stationary threshold.
- Verified all Vectors controls: module/output toggles, elytra and main/offhand spear filters with OR behavior, Circle/Crosshair/Star icons, size 3–31, opacity 5–100%, stationary threshold 0–20 blocks/second, `{magnitude}` validation, velocity placement in HUD Studio, and flat/gradient velocity color scales over the 0–40 default domain.
- Verified that a matching server damage event resets the local fall-distance accumulator only for a non-gliding mace smash above 1.5 blocks, while the hit message retains the attack-time fall-distance snapshot. Unconfirmed attacks and non-smash weapon paths do not reset it.
- Limits: the checks use controlled client entities and injected client packet handling; live multiplayer combat, server-specific damage rules, and live multiplayer flight remain untested.
