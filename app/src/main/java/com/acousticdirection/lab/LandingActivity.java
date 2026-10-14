package com.acousticdirection.lab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;

/** New simplified entry. Engineering diagnostics remain one tap away. */
public final class LandingActivity extends Activity {
    private static final int BG=0xff0b1923,INK=0xffe2f4f1,
        MUTED=0xff9bb4bb,TEAL=0xff58debe,CARD=0xff182e38;
    private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v);}
    private GradientDrawable background(int color,int rad){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(rad));return d;
    }
    private TextView label(String text,int sp,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout column=new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(23),dp(35),dp(23),dp(25));
        scroll.addView(column);
        TextView eyebrow=label("ACOUSTIC  /  DIRECTION LAB",12,TEAL,true);
        column.addView(eyebrow);
        TextView heading=label("Encontre a direção\ndo som.",32,INK,true);
        LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2);
        h.topMargin=dp(15);column.addView(heading,h);
        TextView intro=label("Calibre os microfones do S25 usando a câmera, "+
            "movimento e áudio em tempo real.",14,MUTED,false);
        LinearLayout.LayoutParams i=new LinearLayout.LayoutParams(-1,-2);
        i.topMargin=dp(10);i.bottomMargin=dp(22);column.addView(intro,i);
        card(column,"01","Calibração com câmera","Recomendada",
            "Marque a fonte sonora, movimente o celular e "+
            "valide a direção acústica com ARCore.",
            0xff173c3e,()->startActivity(new Intent(this,ARCalibrationActivity.class)));
        card(column,"02","Teste dos microfones","Diagnóstico",
            "Investigue os canais, os atrasos acústicos "+
            "e a captura estéreo contínua.",
            CARD,()->startActivity(new Intent(this,MainActivity.class)));
        card(column,"03","Arquivos e análises","Exportação",
            "Abra o laboratório técnico para revisar e "+
            "exportar WAV, CSV e sensores.",
            CARD,()->startActivity(new Intent(this,SessionsActivity.class)));
        TextView footer=label("v0.5.1 • Pesquisa experimental • Sem IA\n"+
            "A localização 3D ainda depende de validação acústica.",11,MUTED,false);
        LinearLayout.LayoutParams f=new LinearLayout.LayoutParams(-1,-2);
        f.topMargin=dp(18);column.addView(footer,f);
        setContentView(scroll);
    }
    private void card(LinearLayout column,String number,String name,String subtitle,
                      String description,int tint,Runnable click){
        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(19),dp(15),dp(19),dp(16));
        outer.setBackground(background(tint,18));
        outer.setElevation(dp(3));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(13);column.addView(outer,p);
        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        outer.addView(header);
        TextView index=label(number,12,TEAL,true);
        header.addView(index);
        TextView sub=label(subtitle.toUpperCase(java.util.Locale.ROOT),10,TEAL,true);
        LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(0,-2,1);
        s.leftMargin=dp(12);header.addView(sub,s);
        TextView arrow=label("↗",21,TEAL,true);header.addView(arrow);
        TextView title=label(name,21,INK,true);
        LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(-1,-2);
        t.topMargin=dp(6);outer.addView(title,t);
        TextView desc=label(description,13,MUTED,false);
        LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(-1,-2);
        d.topMargin=dp(6);outer.addView(desc,d);
        outer.setOnClickListener(v->click.run());
        outer.setClickable(true);outer.setFocusable(true);
    }
}
