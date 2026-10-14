package com.acousticdirection.lab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.util.Locale;

/**
 * Debug view of the empirical 1D microphone-axis score, NOT a camera overlay
 * or an angular DOA. Explicitly masks output when unmeasurable.
 */
public final class AxisIndicatorView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private double score=Double.NaN, halfWidth=0;
    private String grade="INDETERMINADO";
    private boolean provisional=false;
    public AxisIndicatorView(Context context){
        super(context);
        setMinimumHeight((int)(getResources().getDisplayMetrics().density*125));
    }
    public void show(double score,double uncertaintyScore,String grade,boolean provisional){
        this.score=score;
        halfWidth=Double.isFinite(uncertaintyScore)?
            Math.max(.015,Math.min(.6,uncertaintyScore)):.15;
        this.grade=grade==null?"INDETERMINADO":grade;
        this.provisional=provisional;
        postInvalidate();
    }
    private void text(Canvas c,String content,float x,float y,float size,int color){
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);
        paint.setTextSize(size);paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(content,x,y,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float den=getResources().getDisplayMetrics().density;
        float w=getWidth(),h=getHeight(),left=24*den,right=w-24*den;
        float middle=.5f*(left+right),y=.45f*h;
        final int fg=0xffd5e5ef,muted=0xff819cab,accent=0xff56dbbc,
            provisionalColor=0xffe5ac55;
        text(canvas,"EIXO FÍSICO DOS MICROFONES — NÃO É ÂNGULO 3D",
            w/2,18*den,10*den,fg);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4*den);paint.setColor(muted);
        canvas.drawLine(left,y,right,y,paint);
        paint.setStrokeWidth(1*den);
        canvas.drawLine(middle,y-10*den,middle,y+10*den,paint);
        text(canvas,"Inferior",left,y+23*den,12*den,fg);
        text(canvas,"Centro",middle,y+23*den,12*den,fg);
        text(canvas,"Superior",right,y+23*den,12*den,fg);
        if(Double.isFinite(score)){
            float center=(float)(middle+Math.max(-1,Math.min(1,score))*(right-left)/2);
            float band=(float)(halfWidth*(right-left)/2);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(11*den);
            paint.setColor(provisional?0x77e5ac55:0x6656dbbc);
            canvas.drawLine(Math.max(left,center-band),y,
                Math.min(right,center+band),y,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(provisional?provisionalColor:accent);
            canvas.drawCircle(center,y,7*den,paint);
            String clamped=Math.abs(score)>1?" • fora do perfil":"";
            text(canvas,String.format(Locale.US,"Índice %+.2f%s",score,clamped),
                w/2,h-29*den,12*den,fg);
            text(canvas,grade,w/2,h-10*den,10*den,muted);
        } else {
            text(canvas,"Direção indisponível: "+grade,w/2,h-20*den,11*den,muted);
        }
    }
}
