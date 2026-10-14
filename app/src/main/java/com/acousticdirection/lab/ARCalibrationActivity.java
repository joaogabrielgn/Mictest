package com.acousticdirection.lab;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ARCore CAMERA + continuous stereo sound, synchronized approximately via
 * BOOTTIME sampled after Session.update(). Calibrate source projection by touch,
 * then FREEZE the learned intercept/slope and validate on NEW motions.
 *
 * No CameraX simultaneous access, no acoustic source classification,
 * no assertion of angular accuracy before independent validation.
 */
public final class ARCalibrationActivity extends Activity {
    private final ArFrameBridge poses=new ArFrameBridge();
    private final AudiovisualCalibration calibration=new AudiovisualCalibration();
    private final WorldDirectionSolver validator3d=new WorldDirectionSolver();
    private GLSurfaceView gl;
    private ArCameraRenderer renderer;
    private ArMarkerOverlay overlay;
    private TextView status,details;
    private Button audioButton,freezeButton;
    private Session session;
    private boolean installRequested=false;
    private boolean glResumed=false;
    private volatile boolean audioRunning=false;
    private volatile AudioRecord recorder;
    private volatile File outputWav,outputCsv;
    // Keep EVERY segment in a session, including training before the model
    // is frozen and independent validation afterward.
    private final java.util.ArrayList<File> capturedSegments=new java.util.ArrayList<>();
    private int windows=0,accepted=0,timestampFails=0,arTimeFails=0;
    private volatile String arTracking="ARCore iniciando";
    private volatile String markerMode="SEM_MARCA";
    private volatile WorldDirectionSolver.Result lastDirection=null;
    private volatile double lastAngularError=Double.NaN;
    private long lastUiNs=0;
    private static final int PERMISSIONS=201,EXPORT_ZIP=202;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        gl=new GLSurfaceView(this);
        gl.setEGLContextClientVersion(2);
        gl.setPreserveEGLContextOnPause(true);
        renderer=new ArCameraRenderer(poses,new ArCameraRenderer.Listener(){
            @Override public void onVisual(int x,int y,boolean visible,String mode,
                            String tracking,String description){
                runOnUiThread(()->{
                    if(overlay!=null)overlay.update(x,y,visible,mode);
                    arTracking=tracking+" • "+description;
                    markerMode=mode;
                    if(!audioRunning)status.setText(arTracking+"\nToque na fonte para definir referência");
                });
            }
            @Override public void onMark(String mode){
                calibration.clear();
                validator3d.reset();
                lastDirection=null;lastAngularError=Double.NaN;
                runOnUiThread(()->{
                    markerMode=mode;
                    details.setText("Fonte marcada: "+mode+
                        "\nNova calibração iniciada. Mantenha a fonte fixa e faça uma varredura.");
                });
            }
            @Override public void onError(String description){
                runOnUiThread(()->status.setText(description));
            }
        });
        gl.setRenderer(renderer);
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(gl,new FrameLayout.LayoutParams(-1,-1));
        overlay=new ArMarkerOverlay(this);
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlay.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_UP){
                renderer.markAt(e.getX(),e.getY());
                return true;
            }
            return true;
        });
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(0xB8112028);top.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView label=label("ACOUSTIC DIRECTION LAB · AR v0.5",15,0xff70e6bf);
        top.addView(label);
        status=label("Iniciando ARCore...",12,Color.WHITE);
        top.addView(status);
        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(top,topParams);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(8),dp(8),dp(8),dp(8));
        bottom.setBackgroundColor(0xD40A1C24);
        details=label("Toque no objeto que emite o som; depois inicie o áudio.",11,0xffc5e8e2);
        details.setMaxLines(4);
        bottom.addView(details);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(buttons);
        audioButton=button(buttons,"Iniciar áudio",()->toggleAudio());
        freezeButton=button(buttons,"Calibrar / Validar",()->freezeAndValidate());
        button(buttons,"Nova fonte",()->reset());
        button(buttons,"Exportar ZIP",()->exportZip());
        FrameLayout.LayoutParams bottomParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        root.addView(bottom,bottomParams);
        setContentView(root);
        requestRequiredPermissions();
    }
    private TextView label(String value,int size,int color){
        TextView v=new TextView(this);v.setText(value);v.setTextColor(color);
        v.setTextSize(size);v.setTypeface(Typeface.MONOSPACE);
        v.setPadding(dp(3),dp(4),dp(3),dp(4));return v;
    }
    private Button button(LinearLayout row,String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(10);
        b.setTextColor(Color.WHITE);b.setOnClickListener(v->action.run());
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff14695f));
        row.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
        return b;
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density);}
    private boolean permitted(){
        return checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
    }
    private void requestRequiredPermissions(){
        if(!permitted())requestPermissions(new String[]{Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO},PERMISSIONS);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==PERMISSIONS){
            if(permitted())startAr();
            else status.setText("Câmera e microfone são necessários para calibração AR.");
        }
    }
    @Override protected void onResume(){
        super.onResume();
        if(permitted())startAr();
    }
    private void startAr(){
        if(glResumed || !permitted())return;
        try{
            if(session==null){
                ArCoreApk.InstallStatus installed=ArCoreApk.getInstance().requestInstall(
                    this,!installRequested);
                if(installed==ArCoreApk.InstallStatus.INSTALL_REQUESTED){
                    installRequested=true;
                    status.setText("Instale/atualize Google Play Services para RA.");
                    return;
                }
                session=new Session(this);
                Config cfg=new Config(session);
                cfg.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                cfg.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                if(session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                    cfg.setDepthMode(Config.DepthMode.AUTOMATIC);
                session.configure(cfg);
            }
            session.resume();
            renderer.setSession(session);
            renderer.setDisplayRotation(getWindowManager().getDefaultDisplay().getRotation());
            gl.onResume();glResumed=true;
            status.setText("ARCore ativo. Aponte a câmera traseira e toque na fonte.");
        }catch(Exception e){
            status.setText("Não foi possível iniciar ARCore: "+
                e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
    @Override protected void onPause(){
        stopAudio();
        if(glResumed){gl.onPause();glResumed=false;}
        if(session!=null){
            try{session.pause();}catch(Exception ignored){}
            renderer.setSession(null);
        }
        super.onPause();
    }
    @Override protected void onDestroy(){
        stopAudio();
        if(session!=null){session.close();session=null;}
        super.onDestroy();
    }
    private void reset(){
        calibration.clear();validator3d.reset();poses.clear();
        lastDirection=null;lastAngularError=Double.NaN;
        // Clear operation is queued on GL thread to avoid touching ARCore
        // anchors from the UI thread.
        gl.queueEvent(()->renderer.clearMark());
        details.setText("Referência anterior apagada. Toque em uma fonte fixa.");
    }
    private void freezeAndValidate(){
        if(calibration.isValidation()){
            AudiovisualCalibration.Validation test=calibration.validate();
            details.setText(test.display()+
                (Double.isFinite(lastAngularError)?
                    String.format(Locale.US,"\nErro angular 3D observacional: %.1f°",
                        lastAngularError):"\n3D ainda não observável"));
            return;
        }
        AudiovisualCalibration.Fit fit=calibration.freeze();
        if(fit.valid){
            validator3d.reset();
            details.setText(fit.display()+
                "\nMODELO CONGELADO. Agora mova novamente o celular para validar"+
                " com medições NOVAS.");
        }else{
            details.setText(fit.display()+"\nIncline também para cima/baixo.");
        }
    }
    private void toggleAudio(){
        if(audioRunning){stopAudio();return;}
        if(!permitted()){requestRequiredPermissions();return;}
        if(!renderer.hasMark()){
            Toast.makeText(this,"Primeiro toque no objeto que produz o som.",
                Toast.LENGTH_LONG).show();return;
        }
        audioRunning=true;windows=0;accepted=0;timestampFails=0;arTimeFails=0;
        audioButton.setText("Parar áudio");
        new Thread(this::audioLoop,"AR-Audio-CAMCORDER").start();
    }
    private void stopAudio(){
        audioRunning=false;
        AudioRecord current=recorder;
        if(current!=null)try{current.stop();}catch(Exception ignored){}
        runOnUiThread(()->{if(audioButton!=null)audioButton.setText("Iniciar áudio");});
    }
    private void audioLoop(){
        AudioRecord ar=null;WavWriter wav=null;BufferedWriter csv=null;
        try{
            int rate=48000, channels=2;
            AudioFormat format=new AudioFormat.Builder().setEncoding(
                AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
            ar=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                .setAudioFormat(format).setBufferSizeInBytes(65536).build();
            if(ar.getState()!=AudioRecord.STATE_INITIALIZED||ar.getChannelCount()!=2)
                throw new IOException("Captura estéreo CAMCORDER indisponível");
            recorder=ar;
            String suffix=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
            File dir=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if(dir==null)dir=getFilesDir();
            dir.mkdirs();
            File wave=new File(dir,"ar_mic_"+suffix+".wav");
            File csvFile=new File(dir,"ar_audio_"+suffix+".csv");
            wav=new WavWriter(wave,channels,rate);
            csv=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile),"UTF-8"));
            outputWav=wave;outputCsv=csvFile;
            csv.write("center_frame,audio_boottime_ns,audio_timestamp_status,ar_pose_boottime_ns,"+
                "ar_frame_timestamp_ns,ar_audio_delta_ms,ar_reference_mode,"+
                "ar_world_source_x,ar_world_source_y,ar_world_source_z,"+
                "axis_world_x,axis_world_y,axis_world_z,known_projection,"+
                "camera_translation_m,angular_speed_deg_s,raw_delay_us,"+
                "acoustic_status,band_quality,eligible,phase,model_offset_us,"+
                "model_gain_us,model_training_mae_us,3d_status,3d_rms_projection,"+
                "3d_angular_error_vs_mark_deg\n");
            ar.startRecording();
            if(ar.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IOException("AudioRecord não iniciou");
            final BufferedWriter out=csv;
            final ContinuousTracker tracker=new ContinuousTracker(channels,rate);
            final AudioClock clock=new AudioClock(rate);
            short[] pcm=new short[4096];
            AudioTimestamp ts=new AudioTimestamp();
            long frames=0;
            long began=SystemClock.elapsedRealtimeNanos();
            while(audioRunning && SystemClock.elapsedRealtimeNanos()-began <
                120_000_000_000L){
                int n=ar.read(pcm,0,pcm.length,AudioRecord.READ_BLOCKING);
                if(n<0){if(!audioRunning)break;throw new IOException("AudioRecord.read="+n);}
                if(n==0)continue;
                n-=n%2;wav.write(pcm,n);frames+=n/2;
                long now=SystemClock.elapsedRealtimeNanos();
                if(ar.getTimestamp(ts,AudioTimestamp.TIMEBASE_BOOTTIME)==AudioRecord.SUCCESS)
                    clock.update(ts.framePosition,ts.nanoTime,frames,now);
                else clock.invalidate();
                tracker.feed(pcm,n,window->{
                    windows++;
                    AudioClock.Mapping mapped=clock.timeForFrame(window.centerFrame);
                    if(!mapped.valid)timestampFails++;
                    ArFrameBridge.Sample pose=mapped.valid?poses.closest(mapped.timeNs):null;
                    if(pose==null)arTimeFails++;
                    double quality=0;
                    for(ContinuousTracker.Band band:window.bands)
                        if(band.valid)
                            quality=Math.max(quality,band.spectralCoherence);
                    boolean clean=pose!=null && mapped.valid && window.levelDb>-52 &&
                        !window.clipped && Double.isFinite(window.rawDelayUs) &&
                        !("TONAL_AMBIGUO".equals(window.status))&& quality>=.12 &&
                        pose.angularSpeedDegSec<=28 &&
                        (pose.anchorMode || pose.translationFromMarkMeters<=.15);
                    boolean admitted=false;
                    if(clean){
                        admitted=calibration.add(mapped.timeNs,
                            pose.expectedProjection(),window.rawDelayUs,quality);
                        if(admitted)accepted++;
                    }
                    AudiovisualCalibration.Fit fit=calibration.isValidation()?
                        calibration.fitted():null;
                    boolean phaseValidation=fit!=null;
                    if(phaseValidation && clean && windows%8==0){
                        double p=(window.rawDelayUs-fit.offsetUs)/fit.gainUs;
                        WorldDirectionSolver.Result result=validator3d.addProjection(
                            mapped.timeNs,orientationFromAxisUnavailable(pose),
                            p,Math.abs(p)<=1.12);
                        lastDirection=result;
                        if(result.available){
                            double[] gt=pose.sourceWorld;
                            double dot=result.world[0]*gt[0]+result.world[1]*gt[1]+
                                result.world[2]*gt[2];
                            lastAngularError=Math.toDegrees(
                                Math.acos(Math.max(-1,Math.min(1,dot))));
                        }
                    }
                    try{
                        out.write(String.format(Locale.US,
                            "%d,%d,%s,%d,%d,%.3f,%s,"+
                            "%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f,%.4f,%.3f,"+
                            "%.4f,%s,%.5f,%d,%s,%.4f,%.4f,%.4f,%s,%.5f,%.4f\n",
                            window.centerFrame,mapped.valid?mapped.timeNs:-1,mapped.status,
                            pose==null?-1:pose.boottimeNs,pose==null?-1:pose.cameraFrameNs,
                            pose==null?Double.NaN:(pose.boottimeNs-mapped.timeNs)/1e6,
                            pose==null?"SEM_ARCORE":pose.anchorMode?"AR_ANCHOR":"DIRECAO_APENAS",
                            pose==null?Double.NaN:pose.sourceWorld[0],
                            pose==null?Double.NaN:pose.sourceWorld[1],
                            pose==null?Double.NaN:pose.sourceWorld[2],
                            pose==null?Double.NaN:pose.axisWorld[0],
                            pose==null?Double.NaN:pose.axisWorld[1],
                            pose==null?Double.NaN:pose.axisWorld[2],
                            pose==null?Double.NaN:pose.expectedProjection(),
                            pose==null?Double.NaN:pose.translationFromMarkMeters,
                            pose==null?Double.NaN:pose.angularSpeedDegSec,
                            window.rawDelayUs,window.status,quality,admitted?1:0,
                            phaseValidation?"VALID":"TRAIN",
                            fit==null?Double.NaN:fit.offsetUs,
                            fit==null?Double.NaN:fit.gainUs,
                            fit==null?Double.NaN:fit.trainingMaeUs,
                            lastDirection==null?"SEM_3D":lastDirection.status,
                            lastDirection==null?Double.NaN:lastDirection.rmsProjection,
                            lastAngularError));
                    }catch(IOException e){
                        throw new IllegalStateException("CSV AR: "+e.getMessage(),e);
                    }
                    long timestamp=SystemClock.elapsedRealtimeNanos();
                    if(timestamp-lastUiNs>320_000_000L){
                        lastUiNs=timestamp;
                        int acceptedLocal=accepted,windowsLocal=windows;
                        AudiovisualCalibration.Fit pilot=phaseValidation?fit:calibration.preview();
                        AudiovisualCalibration.Validation validation=phaseValidation?
                            calibration.validate():null;
                        String report="AR + Áudio | "+markerMode+" | "+arTracking+
                            String.format(Locale.US,
                                "\nJanelas %d | pares aceitos %d | sem pose AR %d"+
                                "\nTDOA %+.1f µs | %.0f°/s",
                                windowsLocal,acceptedLocal,arTimeFails,
                                window.rawDelayUs,pose==null?Double.NaN:pose.angularSpeedDegSec)+
                            "\n"+(validation==null?pilot.display():validation.display())+
                            (phaseValidation&&lastDirection!=null?
                                "\n"+lastDirection.display()+
                                (Double.isFinite(lastAngularError)?
                                    String.format(Locale.US,
                                        "\nERRO 3D vs referência: %.1f° (experimental)",
                                        lastAngularError):""):"");
                        runOnUiThread(()->details.setText(report));
                    }
                });
            }
        }catch(Exception error){
            runOnUiThread(()->status.setText("Falha na gravação: "+error.getMessage()));
        }finally{
            if(ar!=null){try{ar.stop();}catch(Exception ignored){}ar.release();}
            recorder=null;
            if(wav!=null)try{wav.close();}catch(Exception ignored){}
            if(csv!=null)try{csv.close();}catch(Exception ignored){}
            synchronized(capturedSegments){
                if(outputWav!=null && outputWav.exists() &&
                   outputCsv!=null && outputCsv.exists()){
                    // Includes both TRAIN and VALID captures.
                    if(!capturedSegments.contains(outputWav))
                        capturedSegments.add(outputWav);
                    if(!capturedSegments.contains(outputCsv))
                        capturedSegments.add(outputCsv);
                }
            }
            audioRunning=false;
            runOnUiThread(()->audioButton.setText("Iniciar áudio"));
        }
    }
    /**
     * Pending: use the ARCore sensor quaternion stored in each AR frame.
     * Extrapolating it from the axis alone is not mathematically possible.
     */
    private static double[] orientationFromAxisUnavailable(ArFrameBridge.Sample s){
        return s.sensorQuaternion.clone();
    }
    private void exportZip(){
        if(audioRunning){Toast.makeText(this,"Pare a gravação antes de exportar.",
            Toast.LENGTH_LONG).show();return;}
        if(outputWav==null||outputCsv==null||!outputWav.exists()||!outputCsv.exists()){
            Toast.makeText(this,"Grave pelo menos um teste antes de exportar.",
                Toast.LENGTH_LONG).show();return;
        }
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"Acoustic_AR_v05_"+System.currentTimeMillis()+".zip");
        startActivityForResult(intent,EXPORT_ZIP);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=EXPORT_ZIP||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri target=data.getData();
        java.util.ArrayList<File> files;
        synchronized(capturedSegments){files=new java.util.ArrayList<>(capturedSegments);}
        String rows=calibration.exportRows();
        AudiovisualCalibration.Fit fit=calibration.isValidation()?
            calibration.fitted():calibration.preview();
        AudiovisualCalibration.Validation validation=calibration.validate();
        new Thread(()->{
            try(OutputStream destination=getContentResolver().openOutputStream(target);
                ZipOutputStream zip=new ZipOutputStream(destination)){
                for(File file:files)if(file.exists())appendFile(zip,file);
                appendText(zip,"calibration_pairs.csv",rows);
                appendText(zip,"metadata.txt",
                    "AR calibration v0.5, S25, CAMCORDER 2 channels, 48000Hz\n"+
                    "AR capture and audio are aligned by approximate BOOTTIME, not"+
                    " hardware-synchronized. Fast gestures should be excluded.\n"+
                    "Tracking mode on last frame: "+markerMode+"\n"+
                    "Acoustic reference: "+fit.display()+"\n"+
                    "Separate validation: "+validation.display()+"\n"+
                    "Number windows: "+windows+" admitted: "+accepted+
                    " audio clock missing: "+timestampFails+" AR time missing: "+arTimeFails+"\n"+
                    "Angular check requires an AR-labelled true source and fresh validation frames.\n");
                runOnUiThread(()->Toast.makeText(this,
                    "Pacote AR exportado.",Toast.LENGTH_LONG).show());
            }catch(Exception e){
                runOnUiThread(()->status.setText("Falha ZIP: "+e.getMessage()));
            }
        },"AR-ZIP-Export").start();
    }
    private static void appendFile(ZipOutputStream zip,File file)throws IOException{
        zip.putNextEntry(new ZipEntry(file.getName()));
        try(FileInputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))>=0)if(n>0)zip.write(buffer,0,n);
        }
        zip.closeEntry();
    }
    private static void appendText(ZipOutputStream zip,String name,String text)throws IOException{
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
