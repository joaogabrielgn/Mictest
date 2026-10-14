package com.acousticdirection.lab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Screen-space AR marker for the USER-LABELLED source; never a predicted
 * acoustic direction. Prediction/error is displayed separately in text. */
public final class ArMarkerOverlay extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int x=-1,y=-1;
    private boolean marked=false,visible=false;
    private String mode="SEM_MARCA";
    public ArMarkerOverlay(Context context){super(context);}
    public void update(int x,int y,boolean visible,String mode){
        this.x=x;this.y=y;this.visible=visible;
        this.marked=!"SEM_MARCA".equals(mode);this.mode=mode;
        invalidate();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(!marked)return;
        float d=getResources().getDisplayMetrics().density;
        int color=0xff54e0b5;
        p.setColor(color);p.setStrokeWidth(2.5f*d);p.setStyle(Paint.Style.STROKE);
        int width=getWidth(),height=getHeight();
        float xx=x,yy=y;
        if(!visible){
            if(x<0||y<0)return;
            xx=Math.max(20*d,Math.min(width-20*d,x));
            yy=Math.max(20*d,Math.min(height-20*d,y));
        }
        c.drawCircle(xx,yy,17*d,p);
        c.drawLine(xx-29*d,yy,xx-10*d,yy,p);
        c.drawLine(xx+10*d,yy,xx+29*d,yy,p);
        c.drawLine(xx,yy-29*d,xx,yy-10*d,p);
        c.drawLine(xx,yy+10*d,xx,yy+29*d,p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(12*d);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(!visible?"FORA DO QUADRO":
            ("AR_ANCHOR".equals(mode)?"FONTE • ÂNCORA":"FONTE • DIREÇÃO"),
            Math.max(75*d,Math.min(width-75*d,xx)),
            Math.max(24*d,yy-36*d),p);
    }
}
