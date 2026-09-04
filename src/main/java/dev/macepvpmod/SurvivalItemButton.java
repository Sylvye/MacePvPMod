package dev.macepvpmod;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class SurvivalItemButton extends Button {
    private final ItemStack icon;
    private final BooleanSupplier selected;
    SurvivalItemButton(SurvivalItemRule rule, BooleanSupplier selected, Runnable click) {
        super(0, 0, 36, 32, Component.literal(SurvivalItems.name(rule)), b -> click.run(), DEFAULT_NARRATION);
        this.icon = SurvivalItems.icon(rule); this.selected = selected;
        setTooltip(Tooltip.create(Component.literal(SurvivalItems.name(rule) + "\n" + rule.item())));
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
        extractDefaultSprite(g);
        g.item(icon, getX() + 10, getY() + 6);
        if (selected.getAsBoolean()) {
            g.fill(getX() + 2, getY() + getHeight() - 3, getX() + getWidth() - 2, getY() + getHeight() - 1, 0xff55ff88);
            g.text(Minecraft.getInstance().font, "✓", getX() + 25, getY() + 2, 0xff55ff88);
        }
    }
}
