package dev.macepvpmod;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
final class SettingsControls {
    static AbstractSliderButton slider(String label,double initial,double min,double max,double step,int width,DoubleConsumer setter) {
        return new AbstractSliderButton(0,0,width,20,Component.empty(),Math.clamp((initial-min)/(max-min),0,1)) {
            { updateMessage(); }
            double actual() { return Math.clamp(Math.round((min+value*(max-min))/step)*step,min,max); }
            protected void updateMessage() { setMessage(Component.literal(label+": "+String.format(Locale.ROOT,step<1?"%.2f":"%.0f",actual()))); }
            protected void applyValue() { setter.accept(actual()); }
        };
    }
}
