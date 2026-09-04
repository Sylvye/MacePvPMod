package dev.macepvpmod;
public record HudStyle(double anchorX, double anchorY, double alignX, double alignY, double x, double y,
                       double scale, int color, int secondaryColor, int combinedColor, int width, int thickness, double opacity) {
    public HudStyle validated() {
        return new HudStyle(limit(anchorX,0,1,.5),limit(anchorY,0,1,.5),limit(alignX,0,1,.5),limit(alignY,0,1,0),
                limit(x,-4000,4000,0),limit(y,-4000,4000,0),limit(scale,.5,4,1),color & 0xffffff,secondaryColor & 0xffffff,combinedColor & 0xffffff,
                Math.clamp(width,10,400),Math.clamp(thickness,1,8),limit(opacity,.05,1,1));
    }
    static double limit(double v,double a,double b,double fallback) { return Double.isFinite(v)?Math.clamp(v,a,b):fallback; }
    HudStyle edit(double dx,double dy,double size,int c,int c2,int c3,int w,int t,double alpha) {
        return new HudStyle(anchorX,anchorY,alignX,alignY,dx,dy,size,c,c2,c3,w,t,alpha).validated();
    }
    static HudStyle text(double ax,double ay,double alignY,double x,double y,double scale,int color) {
        return new HudStyle(ax,ay,ax,alignY,x,y,scale,color,color,color,100,1,1);
    }
}
