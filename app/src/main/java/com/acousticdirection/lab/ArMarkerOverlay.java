package com.acousticdirection.lab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Two DISTINCT layers:
 * Teal = manually chosen AR reference.
 * Amber = independently reconstructed acoustic direction (VALID only).
 * An AR reference is never presented as the acoustic prediction.
 */
public final class ArMarkerOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int rx=-1,ry=-1,ax=-1,ay=-1;
    private boolean referenceVisible=false,referenceMarked=false,
        acousticVisible=false,acousticAvailable=false;
    public ArMarkerOverlay(Context context){super(context);setClickable(false);}
    public void update(int x,int y,boolean visible,String mode){
        rx=x;ry=y;referenceVisible=visible;
        referenceMarked=mode!=null&&!"SEM_MARCA".equals(mode);
        invalidate();
    }
    public void updatePrediction(int x,int y,boolean visible){
        ax=x;ay=y;acousticVisible=visible;acousticAvailable=x>=0&&y>=0;
        invalidate();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(referenceMarked)marker(c,rx,ry,referenceVisible,0xff55e3bf,
            "FONTE MARCADA",false);
        if(acousticAvailable)marker(c,ax,ay,acousticVisible,0xffffbb69,
            "SOM ESTIMADO",true);
    }
    private void marker(Canvas c,int rawX,int rawY,boolean inImage,int color,
                        String label,boolean dashed){
        if(rawX<0||rawY<0)return;
        float d=getResources().getDisplayMetrics().density;
        float width=getWidth(),height=getHeight();
        float x=Math.max(24*d,Math.min(width-24*d,rawX));
        float y=Math.max(94*d,Math.min(height-200*d,rawY));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x9d0b1b22);
        c.drawCircle(x,y,24*d,paint);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f*d);
        if(dashed){
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{5*d,4*d},0));
        }
        c.drawCircle(x,y,17*d,paint);
        paint.setPathEffect(null);
        c.drawLine(x-30*d,y,x-22*d,y,paint);
        c.drawLine(x+22*d,y,x+30*d,y,paint);
        c.drawLine(x,y-30*d,x,y-22*d,paint);
        c.drawLine(x,y+22*d,x,y+30*d,paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x,y,3*d,paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(11*d);
        paint.setTypeface(android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD));
        String description=inImage?label:"FORA DO QUADRO • "+label;
        float textX=Math.max(105*d,Math.min(width-105*d,x));
        float textY=y-37*d;
        paint.setColor(0xbd0a1b21);
        float textWidth=paint.measureText(description)+16*d;
        c.drawRoundRect(textX-textWidth/2,textY-16*d,
            textX+textWidth/2,textY+6*d,9*d,9*d,paint);
        paint.setColor(color);c.drawText(description,textX,textY,paint);
    }
}
