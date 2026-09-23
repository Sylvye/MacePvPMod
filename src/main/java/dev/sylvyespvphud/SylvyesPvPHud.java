package dev.sylvyespvphud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SylvyesPvPHud implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("sylvyespvphud");
    public static final ConfigStore CONFIG = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud.json"));
    public static final DamageConfigStore DAMAGE_CONFIG = new DamageConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-damage.json"));
    public static final AttributeSwapConfigStore ATTRIBUTE_SWAP_CONFIG = new AttributeSwapConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-attribute-swaps.json"));
    public static final SurvivalConfigStore SURVIVAL_CONFIG = new SurvivalConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-survival.json"));
    public static final HudConfigStore HUD_CONFIG = new HudConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-hud.json"));
    public static final ReachOutlineConfigStore REACH_OUTLINE_CONFIG = new ReachOutlineConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-reach-outlines.json"));
    public static final VectorsConfigStore VECTORS_CONFIG = new VectorsConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-vectors.json"));
    public static final TrackerConfigStore TRACKER_CONFIG = new TrackerConfigStore(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-player-tracker.json"));
    public static final ProfileManager PROFILES = new ProfileManager(FabricLoader.getInstance().getConfigDir().resolve("sylvyespvphud-profiles.json"));
    @Override public void onInitializeClient() {
        migrateLegacyConfigs();
        CONFIG.load();
        SURVIVAL_CONFIG.load();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("sylvyespvphud", "survival_instincts"), SurvivalHud::extract);
        ATTRIBUTE_SWAP_CONFIG.load();
        DAMAGE_CONFIG.load();
        HUD_CONFIG.load();
        REACH_OUTLINE_CONFIG.load();
        VECTORS_CONFIG.load();
        TRACKER_CONFIG.load();
        PROFILES.load(ProfileSettings.current());
        ReachOutlines.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("resetdamagetracker").executes(context->{
            DamageHud.resetTracker();
            var player=Minecraft.getInstance().player;
            if(player!=null)player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Damage tracker reset."));
            return 1;
        })));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("hudprofile")
                .executes(context -> { Minecraft.getInstance().gui.setScreen(new ProfileScreen(null)); return 1; })
                .then(argument("profile", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("auto"), PROFILES.profiles().stream().map(p -> StringArgumentType.escapeIfRequired(p.name()))), builder))
                        .executes(context -> switchProfile(StringArgumentType.getString(context, "profile"))))));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var server = client.getCurrentServer();
            if (!client.isLocalServer() && server != null) {
                try { PROFILES.join(server.ip); }
                catch (IllegalArgumentException error) { LOG.warn("Could not identify connected server for profile selection: {}", server.ip, error); }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PROFILES.disconnect());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("sylvyespvphud", "attribute_swap"), AttributeSwaps::extract);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("sylvyespvphud", "damage_counter"), DamageHud::extract);
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() && player == net.minecraft.client.Minecraft.getInstance().player) {
                AttributeSwaps.successfulHit();
                DamageHud.attacked(entity);
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("sylvyespvphud", "elytra_pitch_bar"), PitchHud::extract);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("sylvyespvphud", "vectors"), VectorsHud::extract);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("sylvyespvphud", "player_tracker"), PlayerTrackerHud::extract);
        KeyMapping settings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sylvyespvphud.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sylvyespvphud", "settings"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DamageHud.tick(client);
            SurvivalHud.tick(client);
            VectorsHud.tick(client);
            AttributeSwaps.endTick();
            while (settings.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new SettingsScreen(null));
            }
        });
    }

    private static int switchProfile(String name) {
        try {
            if (name.equalsIgnoreCase("auto")) { PROFILES.useAutomatic(); message("Using automatic HUD profile selection: " + PROFILES.active().name()); }
            else { PROFILES.switchManual(name); message("HUD profile: " + PROFILES.active().name()); }
            return 1;
        }
        catch (Exception error) { message("Could not switch HUD profile: " + error.getMessage()); return 0; }
    }

    static void message(String value) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(value));
    }

    private static void migrateLegacyConfigs() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        String[] names = {"", "-damage", "-attribute-swaps", "-survival", "-hud", "-reach-outlines", "-vectors", "-player-tracker"};
        for (String suffix : names) {
            Path legacy = configDir.resolve("macepvpmod" + suffix + ".json");
            Path current = configDir.resolve("sylvyespvphud" + suffix + ".json");
            if (Files.exists(legacy) && Files.notExists(current)) {
                try {
                    Files.copy(legacy, current, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e) {
                    LOG.warn("Could not migrate legacy config {}", legacy, e);
                }
            }
        }
    }
}
