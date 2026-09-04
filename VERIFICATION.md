# Verification — September 3, 2026

- `./gradlew build`: passed; 9 unit tests, zero failures or errors.
- `./gradlew runClient`: Minecraft 26.2 reached the title screen with MacePvPMod and Mod Menu 20.0.1 loaded.
- `./gradlew runClientGameTest`: both client game tests passed with Mod Menu installed.
- `./gradlew runClientGameTest -PwithoutModMenu`: both client game tests passed without Mod Menu loaded.
- Rendered and visually inspected basic settings, advanced settings, and the 40° gliding bar. The guide aligns with the crosshair and stays beneath it.
- Client tests cover draft isolation, Cancel, Save, Reset, invalid color validation, advanced switches, gliding versus grounded/non-gliding states, F1, first/third person visibility, menu hiding, death, disconnection, and rendering at requested GUI scales 1/2/3 (Minecraft clamps unsupported scales).
- Unit tests cover signed deviation, target alignment, travel limits, custom target/sensitivity, missing configuration, persistence, invalid file backup/recovery, missing fields, invalid types/schema versions, bounds, and failed saves preserving active configuration.

Environment: macOS ARM64, Java 25.0.4.1, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2. Rendering backend: OpenGL 4.1 Metal.

Limits: Vulkan was not tested. Actual window resizing, spectator mode, the unbound shortcut after user assignment, and clicking the configuration button through Mod Menu itself were not manually exercised. Settings and optional Mod Menu integration compile against the real 26.2 APIs; configuration screens were exercised directly by the client tests. Live multiplayer combat has not been verified; damage reporting uses observed health changes, not damage predictions.


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
