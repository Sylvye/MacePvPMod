package dev.macepvpmod;

import java.util.ArrayList;
import java.util.List;

public record ColorScale(ColorMode mode, int flatColor, double minimum, double maximum, List<GradientKey> keys) {
    public static ColorScale flat(int color) {
        return new ColorScale(ColorMode.FLAT, color, 0, 80, defaultKeys());
    }
    public static ColorScale damageDefault() {
        return new ColorScale(ColorMode.GRADIENT, 0xff0000, 0, 80, defaultKeys());
    }
    private static List<GradientKey> defaultKeys() {
        return List.of(new GradientKey(0,0xffffff),new GradientKey(.25,0xffff00),
                new GradientKey(.5,0xffa500),new GradientKey(1,0xff0000));
    }
    public ColorScale validated() {
        if (mode == null || flatColor < 0 || flatColor > 0xffffff || !Double.isFinite(minimum)
                || !Double.isFinite(maximum) || minimum >= maximum || keys == null || keys.size() < 2)
            throw new IllegalArgumentException("Invalid color scale");
        var copy = new ArrayList<GradientKey>(keys.size());
        double previous = -1;
        for (GradientKey key : keys) {
            if (key == null) throw new IllegalArgumentException("Invalid gradient key");
            key = key.validated();
            if (key.position() <= previous) throw new IllegalArgumentException("Gradient keys must be ordered");
            copy.add(key); previous = key.position();
        }
        if (copy.getFirst().position() != 0 || copy.getLast().position() != 1)
            throw new IllegalArgumentException("Gradient keys must include both endpoints");
        return new ColorScale(mode,flatColor,minimum,maximum,List.copyOf(copy));
    }
    public ColorScale withDomain(double minimum,double maximum) {
        return new ColorScale(mode,flatColor,minimum,maximum,keys);
    }
    public int color(double value) {
        if (mode == ColorMode.FLAT || !Double.isFinite(value)) return flatColor;
        double position = Math.clamp((value-minimum)/(maximum-minimum),0,1);
        GradientKey left=keys.getFirst(),right=keys.getLast();
        for(int i=1;i<keys.size();i++) if(position<=keys.get(i).position()){right=keys.get(i);left=keys.get(i-1);break;}
        double span=right.position()-left.position();
        double t=span==0?0:(position-left.position())/span;
        return rgb(left.color(),right.color(),t);
    }
    static int rgb(int a,int b,double t) {
        int r=(int)Math.round((a>>16&255)+((b>>16&255)-(a>>16&255))*t);
        int g=(int)Math.round((a>>8&255)+((b>>8&255)-(a>>8&255))*t);
        int bl=(int)Math.round((a&255)+((b&255)-(a&255))*t);
        return r<<16|g<<8|bl;
    }
}
