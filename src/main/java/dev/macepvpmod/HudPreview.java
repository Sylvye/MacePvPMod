package dev.macepvpmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared representative HUD content used by settings previews. */
final class HudPreview {
    private HudPreview() {}

    static HudRenderer.Bounds render(GuiGraphicsExtractor g,SettingsSession draft,int element,HudStyle style,
                                     int viewportW,int viewportH,boolean muted) {
        int gray=0x65717d;
        if(element==0) {
            HudStyle shown=muted?colors(style,gray,gray,gray,.35):style;
            return HudRenderer.pitch(g,shown,draft.pitch,draft.pitch.targetPitch(),viewportW,viewportH);
        }
        String text=switch(element) {
            case 1->DamageText.format(draft.damage.fallTemplate(),12.5,18);
            case 2->DamageText.format(draft.damage.hitTemplate(),12.5,18);
            case 3->draft.survival.retotemText();
            case 4->draft.survival.healthText();
            case 5->AttributeSwaps.HUD_TEXT;
            case 6->VectorsText.format(draft.vectors.velocityTemplate(),12.5);
            default->"";
        };
        int color=switch(element) {
            case 1->draft.damage.fallColors().color(12.5);
            case 2->draft.damage.effectiveHitColors().color(18);
            case 6->draft.vectors.velocityColors().color(12.5);
            default->style.color();
        };
        return HudRenderer.textColor(g,text,style,muted?gray:color,viewportW,viewportH);
    }

    static HudStyle centered(HudStyle source) {
        return new HudStyle(.5,.5,.5,.5,0,0,source.scale(),source.color(),source.secondaryColor(),
                source.combinedColor(),source.width(),source.thickness(),source.opacity());
    }

    private static HudStyle colors(HudStyle s,int c,int c2,int c3,double opacity) {
        return new HudStyle(s.anchorX(),s.anchorY(),s.alignX(),s.alignY(),s.x(),s.y(),s.scale(),c,c2,c3,
                s.width(),s.thickness(),opacity);
    }
}
