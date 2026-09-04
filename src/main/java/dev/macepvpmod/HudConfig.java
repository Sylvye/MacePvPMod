package dev.macepvpmod;
public record HudConfig(int schemaVersion, HudStyle pitch, HudStyle fall, HudStyle hit, HudStyle retotem, HudStyle healing, HudStyle attributeSwap) {
    public HudConfig validated() {
        if(schemaVersion != 1 || pitch==null || fall==null || hit==null || retotem==null || healing==null) throw new IllegalArgumentException("Invalid HUD configuration");
        return new HudConfig(1,pitch.validated(),fall.validated(),hit.validated(),retotem.validated(),healing.validated(),attributeSwap == null ? attributeSwapDefault() : attributeSwap.validated());
    }
    static HudStyle attributeSwapDefault() { return HudStyle.text(.5,1,0,0,-68,1,0xffffff); }
    static HudConfig defaults() { return migrate(PitchConfig.defaults(),DamageConfig.defaults(),SurvivalConfig.defaults()); }
    static HudConfig migrate(PitchConfig p,DamageConfig d,SurvivalConfig s) {
        return new HudConfig(1,new HudStyle(.5,.5,.5,.5,0,0,1,p.color(),p.color(),p.color(),p.width(),p.thickness(),p.opacity()),
            HudStyle.text(.5,.5,0,d.fallX(),d.fallY(),d.fallSize(),d.fallColor()),HudStyle.text(.5,.5,0,d.hitX(),d.hitY(),d.hitSize(),d.hitColor()),
            HudStyle.text(s.retotemX()/100,s.retotemY()/100,s.retotemY()/100,0,0,s.retotemSize(),s.retotemColor()),
            new HudStyle(s.healingX()/100,s.healingY()/100,s.healingX()/100,s.healingY()/100,0,0,s.healingSize(),s.healthColor(),s.saturationColor(),s.combinedColor(),100,1,1),attributeSwapDefault());
    }
    HudStyle get(int i) { return switch(i) {case 0->pitch;case 1->fall;case 2->hit;case 3->retotem;case 4->healing;case 5->attributeSwap;default->throw new IllegalArgumentException("Unknown HUD element");}; }
    HudConfig with(int i,HudStyle s) { return new HudConfig(1,i==0?s:pitch,i==1?s:fall,i==2?s:hit,i==3?s:retotem,i==4?s:healing,i==5?s:attributeSwap); }
}
