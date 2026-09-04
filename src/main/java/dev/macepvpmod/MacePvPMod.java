package dev.macepvpmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class MacePvPMod implements ClientModInitializer {
    public static final ConfigStore CONFIG = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("macepvpmod.json"));
    public static final DamageConfigStore DAMAGE_CONFIG = new DamageConfigStore(FabricLoader.getInstance().getConfigDir().resolve("macepvpmod-damage.json"));
    public static final AttributeSwapConfigStore ATTRIBUTE_SWAP_CONFIG = new AttributeSwapConfigStore(FabricLoader.getInstance().getConfigDir().resolve("macepvpmod-attribute-swaps.json"));
    public static final SurvivalConfigStore SURVIVAL_CONFIG = new SurvivalConfigStore(FabricLoader.getInstance().getConfigDir().resolve("macepvpmod-survival.json"));
    public static final HudConfigStore HUD_CONFIG = new HudConfigStore(FabricLoader.getInstance().getConfigDir().resolve("macepvpmod-hud.json"));
    @Override public void onInitializeClient() {
        CONFIG.load();
        SURVIVAL_CONFIG.load();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("macepvpmod", "survival_instincts"), SurvivalHud::extract);
        ATTRIBUTE_SWAP_CONFIG.load();
        DAMAGE_CONFIG.load();
        HUD_CONFIG.load();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("macepvpmod", "attribute_swap"), AttributeSwaps::extract);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("macepvpmod", "damage_counter"), DamageHud::extract);
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() && player == net.minecraft.client.Minecraft.getInstance().player) DamageHud.attacked(entity);
            return net.minecraft.world.InteractionResult.PASS;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("macepvpmod", "elytra_pitch_bar"), PitchHud::extract);
        KeyMapping settings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.macepvpmod.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("macepvpmod", "settings"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DamageHud.tick(client);
            SurvivalHud.tick(client);
            AttributeSwaps.endTick();
            while (settings.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new SettingsScreen(null));
            }
        });
    }
}
