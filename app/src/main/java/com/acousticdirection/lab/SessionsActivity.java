package com.acousticdirection.lab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Local session browser: recover raw WAV/CSV even after leaving the AR screen. */
public final class SessionsActivity extends Activity {
    private static final int BG=0xff0b1923,SURFACE=0xff1b333e,INK=0xffe2f4f1,
        MUTED=0xff9ebac2,TEAL=0xff55dfbc;
    private final ArrayList<File> files=new ArrayList<>();
    private final ArrayList<CheckBox> checks=new ArrayList<>();
    private ArrayList<File> exporting;
    private TextView subtitle;
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable panel(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(15));return d;
    }
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroller=new ScrollView(this);scroller.setBackgroundColor(BG);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(24),dp(18),dp(24));scroller.addView(page);
        TextView title=label("Arquivos e análises",26,INK,true);page.addView(title);
        TextView description=label("Gravações armazenadas neste aparelho. Selecione "+
            "os WAVs e CSVs para revisar ou exportar.",13,MUTED,false);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2);
        dlp.topMargin=dp(8);dlp.bottomMargin=dp(14);page.addView(description,dlp);
        subtitle=label("Procurando...",12,TEAL,true);page.addView(subtitle);
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(48));
        cp.topMargin=dp(10);page.addView(controls,cp);
        button(controls,"Selecionar recentes",()->{
            for(int i=0;i<checks.size();i++)checks.get(i).setChecked(i<6);
        },false);
        button(controls,"Exportar ZIP",this::export,true);
        File dir=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if(dir==null)dir=getFilesDir();
        File[] found=dir.listFiles((f,name)->{
            String n=name.toLowerCase(Locale.ROOT);
            return n.endsWith(".wav")||n.endsWith(".csv")||n.endsWith(".txt");
        });
        if(found!=null){
            Arrays.sort(found,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
            for(int i=0;i<Math.min(found.length,120);i++)files.add(found[i]);
        }
        subtitle.setText(files.size()+" arquivos encontrados");
        for(int i=0;i<files.size();i++){
            File file=files.get(i);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));
            row.setBackground(panel(SURFACE));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(66));
            rp.topMargin=dp(6);page.addView(row,rp);
            CheckBox box=new CheckBox(this);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));
            box.setChecked(i<6);checks.add(box);
            row.addView(box,new LinearLayout.LayoutParams(dp(42),-1));
            LinearLayout middle=new LinearLayout(this);middle.setOrientation(LinearLayout.VERTICAL);
            row.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
            TextView fname=label(file.getName(),12,INK,true);fname.setMaxLines(1);
            fname.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            middle.addView(fname);
            String meta=String.format(Locale.US,"%.1f MB • %s",
                file.length()/1048576.0,new SimpleDateFormat(
                "dd/MM HH:mm",Locale.getDefault()).format(new Date(file.lastModified())));
            middle.addView(label(meta,10,MUTED,false));
            Button preview=new Button(this);preview.setText("Ver");preview.setTextSize(11);
            preview.setAllCaps(false);preview.setTextColor(TEAL);
            preview.setBackground(panel(0xff304c52));
            row.addView(preview,new LinearLayout.LayoutParams(dp(51),dp(39)));
            preview.setOnClickListener(v->showPreview(file));
            row.setOnClickListener(v->box.setChecked(!box.isChecked()));
        }
        if(files.isEmpty()){
            TextView empty=label("Nenhum registro disponível. Faça uma gravação na "+
                "calibração ou no laboratório técnico.",14,MUTED,false);
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);
            ep.topMargin=dp(25);page.addView(empty,ep);
        }
        setContentView(scroller);
    }
    private void button(LinearLayout row,String text,Runnable click,boolean primary){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(12);
        b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?BG:INK);
        b.setBackground(panel(primary?TEAL:SURFACE));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);
        b.setOnClickListener(v->click.run());
    }
    private void showPreview(File file){
        if(!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")&&
           !file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")){
            new AlertDialog.Builder(this).setTitle(file.getName())
                .setMessage(String.format(Locale.US,
                    "Áudio WAV • %.2f MB. Para ouvir ou examinar, exporte pelo ZIP.",
                    file.length()/1048576.0))
                .setPositiveButton("OK",null).show();return;
        }
        StringBuilder preview=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new FileReader(file))){
            String line;int count=0;
            while((line=r.readLine())!=null&&count++<12){
                if(line.length()>280)line=line.substring(0,280)+"…";
                preview.append(line).append('\n');
            }
        }catch(Exception e){preview.append("Não foi possível ler: ").append(e.getMessage());}
        new AlertDialog.Builder(this).setTitle(file.getName())
            .setMessage(preview.toString()).setPositiveButton("OK",null).show();
    }
    private void export(){
        exporting=new ArrayList<>();
        for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked())exporting.add(files.get(i));
        if(exporting.isEmpty()){
            Toast.makeText(this,"Selecione pelo menos um arquivo.",Toast.LENGTH_SHORT).show();return;
        }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"Acoustic_arquivos_"+System.currentTimeMillis()+".zip");
        startActivityForResult(i,301);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=301||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        ArrayList<File> selected=new ArrayList<>(exporting);
        android.net.Uri uri=data.getData();
        new Thread(()->{
            try(OutputStream stream=getContentResolver().openOutputStream(uri);
                ZipOutputStream zip=new ZipOutputStream(stream)){
                for(File file:selected){
                    zip.putNextEntry(new ZipEntry(file.getName()));
                    try(FileInputStream in=new FileInputStream(file)){
                        byte[] buf=new byte[32768];int n;
                        while((n=in.read(buf))!=-1)if(n>0)zip.write(buf,0,n);
                    }
                    zip.closeEntry();
                }
                runOnUiThread(()->Toast.makeText(this,"ZIP salvo.",Toast.LENGTH_LONG).show());
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,
                    "Falha ao exportar: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"Exportar-arquivos").start();
    }
}
