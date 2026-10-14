package com.acousticdirection.lab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/**
 * AR guided training with a separate, truly held-out validation recording.
 *
 * Exposure-time ARCore pose is matched to audio via a delayed queue and
 * interpolated bracketing camera frames. ARCore's camera timebase is not
 * guaranteed by the SDK, hence the runtime FrameClockGate and diagnostics.
 */
public final class ARCalibrationActivity extends Activity {
    private static final int TEAL=0xff4ce0bc,INK=0xffdcecea,DIM=0xffa6bbc3,
        PANEL=0xee12242e,SURFACE=0xff1d3640;
    private static final int PERMISSIONS=201,EXPORT=202;
    private final ArFrameBridge bridge=new ArFrameBridge();
    private final FrameClockGate clockGate=new FrameClockGate();
    private final AudiovisualCalibration calibration=new AudiovisualCalibration();
    private final CalibrationFlow flow=new CalibrationFlow();
    private final WorldDirectionSolver solver=new WorldDirectionSolver();
    private final ArrayList<File> outputFiles=new ArrayList<>();
    private final ArrayList<String> routes=new ArrayList<>();
    private GLSurfaceView gl;
    private ArCameraRenderer renderer;
    private ArMarkerOverlay overlay;
    private Session session;
    private boolean glRunning=false,installRequested=false,detailsExpanded=false;
    private volatile boolean audioRunning=false,threadActive=false;
    private volatile AudioRecord recorder;
    private volatile String tracking="Iniciando câmera",markMode="SEM_MARCA";
    private volatile String markGeometry="";
    private volatile WorldDirectionSolver.Result direction3d;
    private volatile double lastAngularError=Double.NaN;
    private volatile String lastRejection="Aguardando áudio",lastTimestampStatus="—";
    private volatile double lastAgeMs=Double.NaN,lastGapMs=Double.NaN,lastScore=Double.NaN;
    private int windows=0,matched=0,accepted=0,rejected=0;
    private volatile double qualityThreshold=.58;
    private long lastUi=0;
    private LinearLayout steps,techPanel;
    private TextView heading,statusChip,guidance,progressLabel,techDetails;
    private TextView[] metricValues=new TextView[4];
    private Button primary,resetButton,exportButton,techButton;
    private ProgressBar progress;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildInterface();
        if(!permitted())requestPermissions(
            new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},PERMISSIONS);
        refresh();
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value);}
    private GradientDrawable panel(int bg,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(android.graphics.Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private Button action(String value,Runnable run,boolean prominent){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(value);b.setTextSize(13);b.setTypeface(null,Typeface.BOLD);
        b.setTextColor(prominent?0xff06251f:INK);
        b.setBackground(panel(prominent?TEAL:SURFACE,15));
        b.setElevation(dp(2));
        b.setOnClickListener(v->run.run());
        return b;
    }
    private void buildInterface(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff09141b);
        gl=new GLSurfaceView(this);gl.setEGLContextClientVersion(2);
        gl.setPreserveEGLContextOnPause(true);
        renderer=new ArCameraRenderer(bridge,clockGate,new ArCameraRenderer.Listener(){
            @Override public void onVisual(int x,int y,boolean visible,String mode,
                         String state,String message){
                runOnUiThread(()->{
                    overlay.update(x,y,visible,mode);
                    tracking=state+" • "+message;
                    markMode=mode;
                    refresh();
                });
            }
            @Override public void onMark(String mode,float x,float y,int w,int h,float[] projection){
                runOnUiThread(()->{
                    if(threadActive)return;
                    calibration.clear();solver.reset();bridge.clear();
                    outputFiles.clear();routes.clear();
                    direction3d=null;lastAngularError=Double.NaN;
                    markMode=mode;
                    markGeometry=String.format(Locale.US,
                        "pixel=%.1f,%.1f size=%d,%d projection=%s",
                        x,y,w,h,Arrays.toString(projection));
                    flow.mark();
                    refresh();
                });
            }
            @Override public void onError(String message){
                runOnUiThread(()->{
                    tracking=message;refresh();
                });
            }
        });
        gl.setRenderer(renderer);
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(gl,new FrameLayout.LayoutParams(-1,-1));
        overlay=new ArMarkerOverlay(this);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlay.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_UP){
                if(threadActive){
                    Toast.makeText(this,"Pare a gravação antes de marcar outra fonte.",
                        Toast.LENGTH_SHORT).show();
                }else if(e.getY()>dp(88)&&e.getY()<v.getHeight()-dp(180))
                    renderer.markAt(e.getX(),e.getY());
            }
            return true;
        });
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12),dp(9),dp(12),dp(9));
        bar.setBackgroundColor(0xe30b1e27);
        Button back=action("‹",()->finish(),false);
        bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(42)));
        LinearLayout titleBlock=new LinearLayout(this);titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);
        titleLp.setMargins(dp(12),0,0,0);
        titleBlock.addView(text("Calibração audiovisual",16,INK,true));
        titleBlock.addView(text("Câmera AR • Galaxy S25",11,DIM,false));
        bar.addView(titleBlock,titleLp);
        statusChip=text("Aguardando",11,TEAL,true);
        statusChip.setGravity(Gravity.CENTER);statusChip.setPadding(dp(10),dp(7),dp(10),dp(7));
        statusChip.setBackground(panel(SURFACE,15));
        bar.addView(statusChip,new LinearLayout.LayoutParams(-2,dp(35)));
        root.addView(bar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(12),dp(7),dp(12),dp(7));
        sheet.setBackground(panel(PANEL,23));
        steps=new LinearLayout(this);steps.setOrientation(LinearLayout.HORIZONTAL);
        sheet.addView(steps,new LinearLayout.LayoutParams(-1,dp(22)));
        guidance=text("Toque no objeto que está emitindo som",14,INK,true);
        LinearLayout.LayoutParams guideParams=new LinearLayout.LayoutParams(-1,dp(23));
        guideParams.topMargin=dp(4);sheet.addView(guidance,guideParams);
        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        sheet.addView(metrics,new LinearLayout.LayoutParams(-1,dp(45)));
        String[] names={"Pares","Cobertura","Qualidade","Erro"};
        for(int i=0;i<names.length;i++){
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(8),dp(3),dp(6),0);
            cell.setBackground(panel(SURFACE,10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
            lp.setMargins(dp(3),0,dp(3),0);metrics.addView(cell,lp);
            cell.addView(text(names[i],10,DIM,false));
            TextView value=text("—",16,INK,true);
            metricValues[i]=value;cell.addView(value);
        }
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(
            android.content.res.ColorStateList.valueOf(TEAL));
        LinearLayout.LayoutParams progressLp=new LinearLayout.LayoutParams(-1,dp(5));
        progressLp.setMargins(dp(6),dp(4),dp(6),dp(2));sheet.addView(progress,progressLp);
        progressLabel=text("Aguardando referência",11,DIM,false);
        sheet.addView(progressLabel);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsLp=new LinearLayout.LayoutParams(-1,dp(44));
        buttonsLp.topMargin=dp(4);sheet.addView(buttons,buttonsLp);
        primary=action("Marque a fonte",this::primaryAction,true);
        buttons.addView(primary,new LinearLayout.LayoutParams(0,-1,2.3f));
        resetButton=action("Reiniciar",this::reset,false);
        LinearLayout.LayoutParams resetLp=new LinearLayout.LayoutParams(0,-1,1);
        resetLp.leftMargin=dp(6);buttons.addView(resetButton,resetLp);
        exportButton=action("Exportar",this::export,false);
        LinearLayout.LayoutParams exportLp=new LinearLayout.LayoutParams(0,-1,1);
        exportLp.leftMargin=dp(6);buttons.addView(exportButton,exportLp);
        techButton=action("Detalhes técnicos  ▾",()->{
            detailsExpanded=!detailsExpanded;
            techPanel.setVisibility(detailsExpanded?View.VISIBLE:View.GONE);
            techButton.setText(detailsExpanded?"Ocultar detalhes  ▴":"Detalhes técnicos  ▾");
        },false);
        LinearLayout.LayoutParams tLp=new LinearLayout.LayoutParams(-1,dp(29));
        tLp.topMargin=dp(3);sheet.addView(techButton,tLp);
        techPanel=new LinearLayout(this);techPanel.setOrientation(LinearLayout.VERTICAL);
        techPanel.setVisibility(View.GONE);techPanel.setBackground(panel(SURFACE,10));
        techPanel.setPadding(dp(10),dp(8),dp(10),dp(8));
        techDetails=text("Diagnósticos indisponíveis",10,DIM,false);
        techDetails.setMaxLines(7);techPanel.addView(techDetails);
        sheet.addView(techPanel);
        TextView qualityLabel=text("Qualidade mínima (avançado): 58%",11,DIM,false);
        LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(-1,dp(21));
        qlp.topMargin=dp(6);techPanel.addView(qualityLabel,qlp);
        SeekBar threshold=new SeekBar(this);
        threshold.setMax(35);threshold.setProgress(18);
        threshold.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
        threshold.setThumbTintList(android.content.res.ColorStateList.valueOf(TEAL));
        threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean byUser){
                qualityThreshold=.40+value*.01;
                qualityLabel.setText(String.format(Locale.US,
                    "Qualidade mínima (avançado): %.0f%%",qualityThreshold*100));
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        techPanel.addView(threshold,new LinearLayout.LayoutParams(-1,dp(28)));
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        bottom.setMargins(dp(9),0,dp(9),dp(5));
        root.addView(sheet,bottom);
        setContentView(root);
    }
    private void updateSteps(){
        steps.removeAllViews();
        CalibrationFlow.State s=flow.state();
        String[] labels={"1  Marcar","2  Calibrar","3  Validar"};
        int selected=s==CalibrationFlow.State.IDLE?0:
            s==CalibrationFlow.State.SOURCE_MARKED?1:
            (s==CalibrationFlow.State.CALIBRATING||s==CalibrationFlow.State.READY_TO_FREEZE)?1:2;
        for(int i=0;i<labels.length;i++){
            TextView chip=text(labels[i],11,i==selected?0xff102e29:DIM,i==selected);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(panel(i==selected?TEAL:SURFACE,12));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
            p.setMargins(dp(3),0,dp(3),0);steps.addView(chip,p);
        }
    }
    private void refresh(){
        if(primary==null)return;
        CalibrationFlow.State s=flow.state();
        updateSteps();
        String headline,cta,tip;
        if(threadActive){
            cta="Parar gravação";
            headline=flow.phase().equals("VALID")?"Validando":"Calibrando";
            tip="Movimente devagar, mantendo a fonte sonora imóvel";
        } else switch(s){
            case IDLE:headline="Marque uma fonte";cta="Toque na câmera";
                tip="Aponte o S25 para um alto-falante ou motor";break;
            case SOURCE_MARKED:headline="Fonte marcada";cta="Iniciar calibração";
                tip="Gire para os lados e incline para cima e para baixo";break;
            case CALIBRATING:headline="Calibração incompleta";cta="Continuar calibração";
                tip="Colete mais posições, sem mover a fonte";break;
            case READY_TO_FREEZE:headline="Pronto para congelar";cta="Congelar calibração";
                tip="O perfil será fixado; depois faremos outra gravação";break;
            case VALIDATING:headline="Perfil congelado";cta="Iniciar validação";
                tip="Faça uma nova varredura; ela não ajustará o perfil";break;
            default:headline=calibration.validate().status.equals("VALIDACAO_TDOA_OK")?
                "Validação concluída":"Concluído • conferir erro";
                cta="Ver resultado / exportar";
                tip="Abra a exportação para compartilhar WAVs e CSVs";break;
        }
        statusChip.setText(headline);primary.setText(cta);
        guidance.setText(tip);
        primary.setEnabled(s!=CalibrationFlow.State.IDLE||threadActive);
        primary.setAlpha(primary.isEnabled()?1f:.55f);
        resetButton.setEnabled(!threadActive);
        exportButton.setEnabled(!threadActive && !outputFiles.isEmpty());
        exportButton.setAlpha(exportButton.isEnabled()?1f:.5f);
        AudiovisualCalibration.Fit fit=calibration.isValidation()?
            calibration.fitted():calibration.preview();
        int n=flow.phase().equals("VALID")?calibration.validationSize():
            calibration.trainSize();
        metricValues[0].setText(Integer.toString(n));
        metricValues[1].setText(Double.isFinite(fit.projectionRange)?
            String.format(Locale.US,"%.2f",fit.projectionRange):"—");
        double mean=calibration.meanAcceptedQuality();
        metricValues[2].setText(Double.isFinite(mean)?
            String.format(Locale.US,"%.0f%%",mean*100):"—");
        AudiovisualCalibration.Validation val=calibration.validate();
        metricValues[3].setText(flow.phase().equals("VALID")&&
            Double.isFinite(val.maeUs)?
            String.format(Locale.US,"%.0f µs",val.maeUs):
            fit.valid?String.format(Locale.US,"%.0f µs",fit.trainingMaeUs):"—");
        int pct;
        if(s==CalibrationFlow.State.IDLE)pct=0;
        else if(s==CalibrationFlow.State.SOURCE_MARKED)pct=10;
        else if(s==CalibrationFlow.State.VALIDATING||s==CalibrationFlow.State.DONE)
            pct=60+Math.min(40,calibration.validationSize()*2);
        else pct=Math.min(58,10+Math.min(30,n)*2);
        progress.setProgress(pct);
        progressLabel.setText(s==CalibrationFlow.State.READY_TO_FREEZE?
            "Calibração pronta • pare o áudio e congele":
            s==CalibrationFlow.State.VALIDATING?
            "VALID: "+val.count+" pares novos • "+val.status:
            n<24?"Coletando pares válidos ("+n+"/24)":
            fit.valid?"Modelo ajustável • congele antes da validação":
            "Mais variações de ângulo necessárias • "+fit.status);
        techDetails.setText(String.format(Locale.US,
            "AR: %s\nRelógio: %s • idade de imagem %.0f ms\n"+
            "Alinhamento interp. %.1f ms • qualidade do par %.2f\n"+
            "Última rejeição: %s\nJanelas %d • associadas %d • aceitas %d • recusadas %d\n"+
            "Treino: %s\nValidação: %s",
            tracking,clockGate.status(),clockGate.medianFrameAgeMs(),
            lastGapMs,lastScore,lastRejection,windows,matched,accepted,rejected,
            fit.display(),val.display()));
    }
    private void primaryAction(){
        if(threadActive){audioRunning=false;primary.setText("Finalizando...");return;}
        CalibrationFlow.State s=flow.state();
        if(s==CalibrationFlow.State.READY_TO_FREEZE){
            AudiovisualCalibration.Fit trained=calibration.preview();
            if(!trained.valid){flow.ready(false);refresh();return;}
            AudiovisualCalibration.Fit frozen=calibration.freeze();
            if(frozen.valid){
                flow.freeze(SystemClock.elapsedRealtimeNanos());
                solver.reset();direction3d=null;lastAngularError=Double.NaN;
            }
            refresh();return;
        }
        if(s==CalibrationFlow.State.DONE){export();return;}
        if(s==CalibrationFlow.State.IDLE)return;
        startAudio();
    }
    private boolean permitted(){
        return checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED&&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){
        super.onRequestPermissionsResult(code,names,results);
        if(code==PERMISSIONS){
            if(permitted())startAr();
            else {tracking="Permita acesso à câmera e ao microfone";refresh();}
        }
    }
    @Override protected void onResume(){super.onResume();if(permitted())startAr();}
    private void startAr(){
        if(glRunning||!permitted())return;
        try{
            if(session==null){
                ArCoreApk.InstallStatus status=ArCoreApk.getInstance().requestInstall(
                    this,!installRequested);
                if(status==ArCoreApk.InstallStatus.INSTALL_REQUESTED){
                    installRequested=true;tracking="Instale Google Play Services para RA";
                    refresh();return;
                }
                session=new Session(this);
                Config cfg=new Config(session);
                cfg.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                cfg.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                if(session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                    cfg.setDepthMode(Config.DepthMode.AUTOMATIC);
                session.configure(cfg);
            }
            session.resume();renderer.setSession(session);
            renderer.setDisplayRotation(getWindowManager().getDefaultDisplay().getRotation());
            gl.onResume();glRunning=true;
        }catch(Exception e){tracking="ARCore: "+e.getClass().getSimpleName()+": "+e.getMessage();refresh();}
    }
    @Override protected void onPause(){
        audioRunning=false;
        if(glRunning){gl.onPause();glRunning=false;}
        if(session!=null){
            try{session.pause();}catch(Exception ignored){}
            renderer.setSession(null);
        }
        super.onPause();
    }
    @Override protected void onDestroy(){
        audioRunning=false;
        if(session!=null){session.close();session=null;}
        super.onDestroy();
    }
    private void reset(){
        if(threadActive)return;
        calibration.clear();solver.reset();bridge.clear();clockGate.reset();
        outputFiles.clear();routes.clear();direction3d=null;
        lastAngularError=Double.NaN;
        try{flow.clear();}catch(Exception ignored){}
        gl.queueEvent(()->renderer.clearMark());
        overlay.update(-1,-1,false,"SEM_MARCA");
        refresh();
    }
    private void startAudio(){
        if(!permitted()){tracking="Autorize microfone e câmera";refresh();return;}
        if(!renderer.hasMark()){tracking="Toque na fonte sonora";refresh();return;}
        try{flow.begin();}catch(Exception e){
            Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();return;
        }
        windows=matched=accepted=rejected=0;
        audioRunning=true;threadActive=true;refresh();
        final String phase=flow.phase();
        new Thread(()->capture(phase),"Acoustic-AR-"+phase).start();
    }
    private static final class Pending {
        final ContinuousTracker.Window window;
        final long timeNs;
        final String stampStatus;
        Pending(ContinuousTracker.Window w,long t,String status){
            window=w;timeNs=t;stampStatus=status;
        }
    }
    private void capture(String phase){
        AudioRecord ar=null;WavWriter wav=null;BufferedWriter out=null;
        File waveFile=null,csvFile=null;
        try{
            AudioFormat fmt=new AudioFormat.Builder().setEncoding(
                AudioFormat.ENCODING_PCM_16BIT).setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
            ar=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                .setAudioFormat(fmt).setBufferSizeInBytes(65536).build();
            if(ar.getState()!=AudioRecord.STATE_INITIALIZED||ar.getChannelCount()!=2)
                throw new IOException("Estéreo CAMCORDER não disponível");
            recorder=ar;
            File dir=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if(dir==null)dir=getFilesDir();
            dir.mkdirs();
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
            String prefix=phase.equals("TRAIN")?"train":"valid";
            waveFile=new File(dir,prefix+"_audio_"+stamp+".wav");
            csvFile=new File(dir,prefix+"_windows_"+stamp+".csv");
            wav=new WavWriter(waveFile,2,48000);
            out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile),"UTF-8"));
            out.write("phase,center_frame,audio_boottime_ns,audio_stamp_status,"+
                "ar_exposure_boottime_ns,ar_frame_timestamp_ns,ar_processed_boottime_ns,"+
                "frame_age_ms,interp_gap_ms,ar_mode,tracking_clock_status,"+
                "known_projection,angle_speed_deg_s,translation_m,"+
                "delay_us,level_dbfs,spectral_flatness,sound_status,"+
                "best_coherence,best_peak_ratio,best_alt_ratio,pair_quality_score,"+
                "accepted,rejection_reason,fit_offset_us,fit_gain_us,"+
                "3d_status,3d_rms_projection,angular_error_deg\n");
            ar.startRecording();
            if(ar.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IOException("Falha ao iniciar microfones");
            StringBuilder route=new StringBuilder(prefix+":");
            try{for(android.media.MicrophoneInfo mic:ar.getActiveMicrophones())
                route.append(" id=").append(mic.getId())
                    .append(" ").append(mic.getDescription())
                    .append(" mapping=").append(mic.getChannelMapping());}
            catch(Exception e){route.append(" rota indisponível: ").append(e);}
            synchronized(routes){routes.add(route.toString());}
            final AudioClock clock=new AudioClock(48000);
            final ContinuousTracker tracker=new ContinuousTracker(2,48000);
            ArrayDeque<Pending> pending=new ArrayDeque<>();
            short[] buffer=new short[4096];AudioTimestamp stampAudio=new AudioTimestamp();
            long frames=0,begin=SystemClock.elapsedRealtimeNanos();
            while(audioRunning && SystemClock.elapsedRealtimeNanos()-begin<120_000_000_000L){
                int n=ar.read(buffer,0,buffer.length,AudioRecord.READ_BLOCKING);
                if(n<0){if(!audioRunning)break;throw new IOException("AudioRecord.read="+n);}
                n-=n%2;if(n==0)continue;
                wav.write(buffer,n);frames+=n/2;
                long now=SystemClock.elapsedRealtimeNanos();
                if(ar.getTimestamp(stampAudio,AudioTimestamp.TIMEBASE_BOOTTIME)==AudioRecord.SUCCESS)
                    clock.update(stampAudio.framePosition,stampAudio.nanoTime,frames,now);
                tracker.feed(buffer,n,w->{
                    AudioClock.Mapping mapping=clock.timeForFrame(w.centerFrame);
                    pending.addLast(new Pending(w,mapping.valid?mapping.timeNs:-1,mapping.status));
                    windows++;
                });
                flushPending(pending,out,phase,false);
            }
            flushPending(pending,out,phase,true);
        }catch(Exception e){
            final String message=e.getMessage();
            runOnUiThread(()->Toast.makeText(this,
                "Falha na captura: "+message,Toast.LENGTH_LONG).show());
        }finally{
            audioRunning=false;
            if(ar!=null){try{ar.stop();}catch(Exception ignored){}ar.release();}
            recorder=null;
            if(wav!=null)try{wav.close();}catch(Exception ignored){}
            if(out!=null)try{out.close();}catch(Exception ignored){}
            if(waveFile!=null&&waveFile.exists()&&csvFile!=null&&csvFile.exists())
                synchronized(outputFiles){
                    outputFiles.add(waveFile);outputFiles.add(csvFile);
                }
            flow.stop();threadActive=false;
            if(phase.equals("TRAIN"))flow.ready(calibration.preview().valid);
            else if(calibration.validationSize()>=12){
                try{flow.complete();}catch(Exception ignored){}
            }
            runOnUiThread(this::refresh);
        }
    }
    private void flushPending(ArrayDeque<Pending> pending,BufferedWriter out,
                              String phase,boolean force) throws IOException {
        long wall=SystemClock.elapsedRealtimeNanos();
        while(!pending.isEmpty()){
            Pending event=pending.getFirst();
            long latest=bridge.newestExposureNs();
            boolean needsFutureFrame=event.timeNs>0 &&
                latest<event.timeNs+3_000_000L;
            boolean expired=event.timeNs<=0||wall-event.timeNs>850_000_000L;
            if(needsFutureFrame&&!force&&!expired)break;
            pending.removeFirst();
            processPending(event,out,phase,
                needsFutureFrame?"FRAME_NAO_CHEGOU":null);
        }
    }
    private void processPending(Pending event,BufferedWriter out,String phase,
                                String preliminaryRejection)throws IOException{
        ArFrameBridge.Match match=event.timeNs>0?
            bridge.interpolate(event.timeNs):
            new ArFrameBridge.Match(null,"SEM_TIMESTAMP_AUDIO",Double.NaN,Double.NaN);
        ArFrameBridge.Sample pose=match.pose;
        double bestCoherence=0,bestPeak=0,bestAlternative=1;
        for(ContinuousTracker.Band b:event.window.bands){
            if(b.valid && b.spectralCoherence>bestCoherence){
                bestCoherence=b.spectralCoherence;
                bestPeak=b.peakRatio;bestAlternative=b.alternatePeakRatio;
            }
        }
        boolean synced=preliminaryRejection==null && pose!=null&&clockGate.compatible();
        PairQuality.Verdict quality=PairQuality.evaluate(
            bestCoherence,bestPeak,bestAlternative,event.window.levelDb,
            event.window.clipped,match.maxBracketMs,
            pose==null?Double.POSITIVE_INFINITY:pose.angularSpeedDegSec,
            pose==null?Double.POSITIVE_INFINITY:pose.translationFromMarkMeters,
            pose!=null&&pose.anchorMode,synced,
            "TONAL_AMBIGUO".equals(event.window.status),
            event.window.temporalStable,qualityThreshold);
        String rejection=preliminaryRejection!=null?preliminaryRejection:
            pose==null?match.status:quality.reason;
        boolean validPair=quality.accepted && flow.canCollect(event.timeNs);
        if(pose!=null)matched++;
        boolean saved=false;
        if(validPair) {
            saved=calibration.add(event.timeNs,pose.expectedProjection(),
                event.window.rawDelayUs,quality.score);
            if(saved)accepted++;
        }
        if(!saved)rejected++;
        if("TRAIN".equals(phase)&&saved && accepted%4==0)
            flow.ready(calibration.preview().valid);
        AudiovisualCalibration.Fit frozen=calibration.fitted();
        if("VALID".equals(phase)&&saved&&frozen!=null){
            double projected=frozen.fromDelay(event.window.rawDelayUs);
            if(Double.isFinite(projected)&&Math.abs(projected)<=1.12){
                WorldDirectionSolver.Result r=solver.addProjection(event.timeNs,
                    pose.sensorQuaternion,projected,true);
                direction3d=r;
                if(r.available){
                    double[] gt=pose.sourceWorld;
                    double dot=r.world[0]*gt[0]+r.world[1]*gt[1]+r.world[2]*gt[2];
                    lastAngularError=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,dot))));
                }
            }
        }
        lastRejection=saved?"ACEITO":rejection;lastGapMs=match.maxBracketMs;
        lastAgeMs=pose==null?Double.NaN:pose.frameAgeMs();
        lastScore=quality.score;lastTimestampStatus=event.stampStatus;
        out.write(String.format(Locale.US,
            "%s,%d,%d,%s,%d,%d,%d,%.4f,%.4f,%s,%s,"+
            "%.7f,%.3f,%.4f,%.4f,%.3f,%.6f,%s,"+
            "%.5f,%.4f,%.4f,%.4f,%d,%s,%.4f,%.4f,%s,%.6f,%.4f\n",
            phase,event.window.centerFrame,event.timeNs,event.stampStatus,
            pose==null?-1:pose.boottimeNs,pose==null?-1:pose.cameraFrameNs,
            pose==null?-1:pose.processedAtNs,pose==null?Double.NaN:pose.frameAgeMs(),
            match.maxBracketMs,pose==null?"SEM_POSE":
                pose.anchorMode?"AR_ANCHOR":"DIRECAO_APENAS",clockGate.status(),
            pose==null?Double.NaN:pose.expectedProjection(),
            pose==null?Double.NaN:pose.angularSpeedDegSec,
            pose==null?Double.NaN:pose.translationFromMarkMeters,
            event.window.rawDelayUs,event.window.levelDb,event.window.flatness,
            event.window.status,bestCoherence,bestPeak,bestAlternative,
            quality.score,saved?1:0,lastRejection,
            frozen==null?Double.NaN:frozen.offsetUs,
            frozen==null?Double.NaN:frozen.gainUs,
            direction3d==null?"SEM_3D":direction3d.status,
            direction3d==null?Double.NaN:direction3d.rmsProjection,
            lastAngularError));
        long now=SystemClock.elapsedRealtimeNanos();
        if(now-lastUi>240_000_000L){
            lastUi=now;runOnUiThread(this::refresh);
        }
    }
    private void export(){
        if(threadActive){Toast.makeText(this,"Pare a captura para exportar",Toast.LENGTH_SHORT).show();return;}
        synchronized(outputFiles){if(outputFiles.isEmpty()){
            Toast.makeText(this,"Faça uma gravação antes de exportar",Toast.LENGTH_SHORT).show();return;
        }}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"Acoustic_AR_v051_"+System.currentTimeMillis()+".zip");
        startActivityForResult(i,EXPORT);
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();
        ArrayList<File> files;
        synchronized(outputFiles){files=new ArrayList<>(outputFiles);}
        String train=calibration.exportPhase("TRAIN"),
            valid=calibration.exportPhase("VALID");
        AudiovisualCalibration.Fit fit=calibration.fitted();
        AudiovisualCalibration.Validation val=calibration.validate();
        double angle=lastAngularError;
        String geometry=markGeometry;
        String route;
        synchronized(routes){route=android.text.TextUtils.join("\n",routes);}
        int totalWindows=windows,totalMatched=matched,totalAccepted=accepted;
        String phase=flow.state().toString();
        new Thread(()->{
            try(OutputStream target=getContentResolver().openOutputStream(uri);
                ZipOutputStream zip=new ZipOutputStream(target)){
                for(File f:files)appendFile(zip,f);
                append(zip,"train_pairs.csv",train);
                append(zip,"valid_pairs.csv",valid);
                JSONObject json=new JSONObject();
                json.put("version","0.5.1");
                json.put("status",phase);
                json.put("training_pairs",calibration.trainSize());
                json.put("validation_pairs",calibration.validationSize());
                json.put("quality_mean",finite(calibration.meanAcceptedQuality()));
                json.put("training_offset_us",finite(fit==null?Double.NaN:fit.offsetUs));
                json.put("training_gain_us",finite(fit==null?Double.NaN:fit.gainUs));
                json.put("training_mae_us",finite(fit==null?Double.NaN:fit.trainingMaeUs));
                json.put("coverage_projection",finite(fit==null?Double.NaN:fit.projectionRange));
                json.put("validation_mae_us",finite(val.maeUs));
                json.put("validation_baseline_mae_us",finite(val.baselineMaeUs));
                json.put("validation_status",val.status);
                json.put("angular_error_deg",finite(angle));
                json.put("angular_error_is_independent_3d",false);
                json.put("active_microphones",route);
                json.put("camera_marker",geometry);
                json.put("camera_clock_mode",clockGate.status());
                json.put("camera_age_median_ms",finite(clockGate.medianFrameAgeMs()));
                json.put("window_count_last_segment",totalWindows);
                json.put("matched_last_segment",totalMatched);
                json.put("accepted_last_segment",totalAccepted);
                append(zip,"summary.json",json.toString(2));
                append(zip,"metadata.txt","v0.5.1 experimental\n"+
                    "Audio BOOTTIME vs ARCore Frame.getTimestamp checked at runtime.\n"+
                    "ARCore frame timestamp timebase is not formally guaranteed;\n"+
                    "plausible timing is necessary but not sufficient for accuracy.\n"+
                    "TRAIN and VALID use separate WAV/CSV recordings.\n"+
                    "3D error is relative to user's AR marker, not laboratory ground truth.\n");
                runOnUiThread(()->Toast.makeText(this,"ZIP exportado",Toast.LENGTH_LONG).show());
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,"Erro: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"AR-export").start();
    }
    private static Object finite(double value){
        return Double.isFinite(value)?value:JSONObject.NULL;
    }
    private static void appendFile(ZipOutputStream zip,File f)throws IOException{
        zip.putNextEntry(new ZipEntry(f.getName()));
        try(FileInputStream in=new FileInputStream(f)){
            byte[] buf=new byte[32768];int n;
            while((n=in.read(buf))!=-1)zip.write(buf,0,n);
        }
        zip.closeEntry();
    }
    private static void append(ZipOutputStream zip,String path,String content)throws IOException{
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
