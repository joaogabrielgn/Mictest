package com.acousticdirection.lab;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private final String[] sourceNames = {"UNPROCESSED", "MIC", "CAMCORDER", "VOICE_RECOGNITION"};
    private final int[] sourceTypes = {MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_RECOGNITION};
    private final int[] rates = {48000, 96000, 44100};
    private final StringBuilder report = new StringBuilder();
    private Spinner sourceSpinner, channelSpinner, rateSpinner, referenceSpinner, modeSpinner;
    private Button startButton, stopButton;
    private TextView live, log;
    private AxisIndicatorView axisIndicator;
    private AudioManager manager;
    private volatile boolean running = false;
    private volatile AudioRecord current;
    private File lastWav, lastCsv, lastImu, lastContinuousCsv;
    private MotionTracker motion;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        motion = new MotionTracker(this);
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(Color.rgb(12, 24, 36));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15), dp(24), dp(15), dp(20));
        scroller.addView(root);
        setContentView(scroller);
        title(root, "ACOUSTIC DIRECTION LAB", 21, 0xFF62E0C3);
        title(root, "Coerência + IMU + direção 1D / v0.4.2", 14, Color.LTGRAY);
        modeSpinner=selector(root,"Modo de análise",
            new String[]{"SOM_CONTINUO","ESTALOS_CALIBRACAO"},0);
        sourceSpinner = selector(root, "Fonte", sourceNames, 2);
        channelSpinner = selector(root, "Número de canais", new String[]{"1 canal", "2 canais", "3 canais", "4 canais"}, 1);
        rateSpinner = selector(root, "Frequência", new String[]{"48000 Hz", "96000 Hz", "44100 Hz"}, 0);
        referenceSpinner=selector(root,"Posição de referência (opcional)",
            new String[]{"NÃO_ROTULADA","MIC_INFERIOR","MIC_SUPERIOR","CENTRO","VARREDURA_FONTE_FIXA"},0);
        LinearLayout controls = row(root);
        startButton = button(controls, "Iniciar", 0xFF00796B, () -> start());
        stopButton = button(controls, "Parar", 0xFF984348, () -> {running = false; stopButton.setEnabled(false);});
        stopButton.setEnabled(false);
        LinearLayout actions = row(root);
        button(actions, "Listar microfones", 0xFF34546A, () -> listMicrophones());
        button(actions, "Copiar relatório", 0xFF34546A, () -> copyReport());
        LinearLayout exports = row(root);
        button(exports, "Exportar TXT", 0xFF34546A, () -> export(101));
        button(exports, "Exportar WAV", 0xFF34546A, () -> export(102));
        LinearLayout datasets = row(root);
        button(datasets, "Exportar análise CSV", 0xFF34546A, () -> export(103));
        button(datasets, "Exportar IMU CSV", 0xFF34546A, () -> export(104));
        LinearLayout continuousExports=row(root);
        button(continuousExports,"Exportar contínuo CSV",0xFF34546A,()->export(105));
        title(root, "Estimativa contínua / TDOA (experimental)", 17, Color.WHITE);
        live = new TextView(this);
        live.setTextColor(0xFF9BF0DF); live.setTextSize(13);
        live.setTypeface(Typeface.MONOSPACE);
        live.setPadding(dp(8), dp(8), dp(8), dp(8));
        live.setBackgroundColor(0xFF1B3844);
        live.setText("Aguardando teste...");
        root.addView(live);
        axisIndicator=new AxisIndicatorView(this);
        root.addView(axisIndicator,new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(145)));
        title(root, "Relatório técnico", 17, Color.WHITE);
        log = new TextView(this);
        log.setTextColor(Color.WHITE); log.setTextSize(12);
        log.setTypeface(Typeface.MONOSPACE); log.setTextIsSelectable(true);
        root.addView(log);
        writeLog("Acoustic Direction Lab v0.4.2");
        writeLog("Direção visual: índice calibrado de microfones, SEM azimute, distância nem posição na câmera.");
        writeLog("Rotação com quaternions: estabilidade temporal só é exibida após período parado.");
        writeLog("Atrasos de banda única podem ser candidatos ou temporalmente estáveis.");
        writeLog("TEMPORAL_ESTAVEL não mede acurácia angular nem resolve múltiplas fontes.");
        writeLog("SOM_CONTINUO: janelas 85 ms/hop 21 ms; GCC-PHAT multibanda.");
        writeLog("Não separa fontes; a banda dominante não garante a fonte mais alta.");
        writeLog("Perfil S25 PILOTO: microfone inferior -434,7 us, centro -2,8 us, superior +472,1 us.");
        writeLog("Índice 1D não é ângulo nem localização validada.");
        writeLog("Eventos analisados individualmente; timestamp audio BOOTTIME; pose IMU interpolada.");
        writeLog("Leituras de GCC-PHAT são hipóteses, não direções calibradas.");
        writeLog(motion.inventory());
        writeLog("Modelo: " + Build.MANUFACTURER + " " + Build.MODEL
            + " | Android " + Build.VERSION.RELEASE);
        if (!hasPermission()) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        else listMicrophones();
    }
    private int dp(int x) { return (int)(x * getResources().getDisplayMetrics().density); }
    private void title(LinearLayout parent, String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(14), 0, dp(7));
        parent.addView(v);
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL); parent.addView(r);
        return r;
    }
    private Spinner selector(LinearLayout parent, String label, String[] values, int selected) {
        title(parent, label, 13, Color.LTGRAY);
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a); s.setSelection(selected); parent.addView(s);
        return s;
    }
    private Button button(LinearLayout parent, String label, int color, Runnable callback) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(11); b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        b.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        parent.addView(b, lp);
        return b;
    }
    private boolean hasPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    private void writeLog(String s) {
        synchronized(report) { report.append(s).append('\n'); }
        runOnUiThread(() -> { if (log != null) log.append(s + "\n"); });
    }
    private String reportText() { synchronized(report) { return report.toString(); } }
    private String micDetails(MicrophoneInfo m) {
        StringBuilder mapping = new StringBuilder();
        for (android.util.Pair<Integer,Integer> p: m.getChannelMapping()) {
            if (mapping.length() > 0) mapping.append(", ");
            mapping.append("C").append(p.first).append(":")
                .append(p.second == MicrophoneInfo.CHANNEL_MAPPING_DIRECT ? "DIRECT" :
                    p.second == MicrophoneInfo.CHANNEL_MAPPING_PROCESSED ? "PROCESSED" : String.valueOf(p.second));
        }
        MicrophoneInfo.Coordinate3F p = m.getPosition();
        MicrophoneInfo.Coordinate3F o = m.getOrientation();
        return "id=" + m.getId() + ", name=" + m.getDescription()
            + ", type=" + m.getType() + ", location=" + m.getLocation()
            + ", group=" + m.getGroup()
            + ", pos=(" + p.x + "," + p.y + "," + p.z + ")"
            + ", orient=(" + o.x + "," + o.y + "," + o.z + ")"
            + ", mapping=[" + mapping + "]";
    }
    private void listMicrophones() {
        if (!hasPermission()) { writeLog("Permissão de microfone ausente."); return; }
        writeLog("\n=== INVENTÁRIO ===");
        writeLog("Suporte UNPROCESSED: " + manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
        for (AudioDeviceInfo d: manager.getDevices(AudioManager.GET_DEVICES_INPUTS))
            writeLog("Entrada " + d.getProductName() + ", type=" + d.getType()
                + ", channels=" + Arrays.toString(d.getChannelCounts())
                + ", rates=" + Arrays.toString(d.getSampleRates()));
        try {
            java.util.List<MicrophoneInfo> mics = manager.getMicrophones();
            writeLog("Microfones declarados: " + mics.size());
            for (int i = 0; i < mics.size(); i++) writeLog("[" + i + "] " + micDetails(mics.get(i)));
        } catch (Exception e) { writeLog("Falha ao listar microfones: " + e); }
        writeLog("Aviso: microfones declarados NÃO garantem captura independente.");
    }
    private void start() {
        if (running) return;
        if (!hasPermission()) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10); return; }
        final int sourceIndex = sourceSpinner.getSelectedItemPosition();
        final int channels = channelSpinner.getSelectedItemPosition() + 1;
        final int rate = rates[rateSpinner.getSelectedItemPosition()];
        final boolean continuousMode=modeSpinner.getSelectedItemPosition()==0;
        final String knownReference=(String)referenceSpinner.getSelectedItem();
        running = true; startButton.setEnabled(false); stopButton.setEnabled(true);
        motion.start();
        new Thread(() -> record(sourceIndex, channels, rate,continuousMode,knownReference), "CapturePCM").start();
    }
    private void record(int sourceIndex, int channelCount, int rate,
                        boolean continuousMode,String knownReference) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        AudioRecord ar = null;
        WavWriter wav = null;
        BufferedWriter csvOut = null, imuOut = null, continuousOut = null;
        long frames = 0;
        final int[] processedEvents = {0}, candidates = {0}, syncedEvents = {0}, robustCount = {0}, repeatableCount={0};
        // Reference label is captured by start() on UI thread.
        EventDetector detector = null;
        AudioClock clock = null;
        try {
            AudioFormat.Builder b = new AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT);
            if (channelCount == 1) b.setChannelMask(AudioFormat.CHANNEL_IN_MONO);
            else if (channelCount == 2) b.setChannelMask(AudioFormat.CHANNEL_IN_STEREO);
            else b.setChannelIndexMask((1 << channelCount) - 1);
            ar = new AudioRecord.Builder().setAudioSource(sourceTypes[sourceIndex])
                .setAudioFormat(b.build())
                .setBufferSizeInBytes(Math.max(65536, rate * channelCount))
                .build();
            if (ar.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IOException("AudioRecord não inicializado");
            current = ar;
            final int actualChannels = ar.getChannelCount();
            final int actualRate = ar.getSampleRate();
            if (actualChannels <= 0) throw new IOException("Contagem de canais inválida");
            detector = new EventDetector(actualChannels,actualRate);
            clock = new AudioClock(actualRate);
            final AudioClock audioClock = clock;
            final ContinuousTracker tracker=continuousMode && actualChannels>=2 ?
                new ContinuousTracker(actualChannels,actualRate):null;
            final int[] continuousCount={0},continuousReliable={0},continuousStable={0},continuousCandidates={0},
                motionPlausible={0},motionRejected={0};
            final MotionEvidence motionEvidence=new MotionEvidence();
            final long[] stationarySinceNs={-1};
            final long[] lastContinuousUi={0};
            final DelayStability[] stability=new DelayStability[Math.max(0,actualChannels-1)];
            for(int i=0;i<stability.length;i++)stability[i]=new DelayStability();
            writeLog("\n=== TESTE: " + sourceNames[sourceIndex] + ", pedido="
                + channelCount + "ch / " + rate + "Hz ===");
            writeLog("Captura resultante: " + actualChannels + "ch / " + actualRate
                + "Hz; session=" + ar.getAudioSessionId());

            File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (dir == null) dir = getFilesDir();
            dir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            // Paths are published only after successful file creation, to avoid exporting stale files.
            File wavFile = new File(dir, "mic_" + timestamp + "_" + sourceNames[sourceIndex]
                + "_" + actualChannels + "ch.wav");
            File csvFile = new File(dir, "analysis_" + timestamp + "_" + sourceNames[sourceIndex] + ".csv");
            File imuFile = new File(dir, "imu_" + timestamp + "_" + sourceNames[sourceIndex] + ".csv");
            File continuousFile=new File(dir,"continuous_"+timestamp+"_"+sourceNames[sourceIndex]+".csv");
            wav = new WavWriter(wavFile, actualChannels, actualRate);
            csvOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8"));
            imuOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(imuFile), "UTF-8"));
            if(tracker!=null)continuousOut=new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(continuousFile),"UTF-8"));
            lastWav=wavFile;lastCsv=csvFile;lastImu=imuFile;
            lastContinuousCsv=tracker!=null?continuousFile:null;
            final BufferedWriter eventOutput=csvOut;
            final BufferedWriter motionOutput=imuOut;
            final BufferedWriter continuousOutput=continuousOut;
            csvOut.write("onset_frame,window_start_frame,audio_boottime_ns,audio_time_status,anchor_frame,anchor_boottime_ns,channel_B,lag_samples,delay_B_minus_C0_us,peak_over_rms,contrast,rms_C0_dbfs,rms_B_dbfs,clipping_fraction,relative_difference,valid,reason,trigger_ratio,pose_status,pose_time_ns,qw,qx,qy,qz,yaw_deg,pitch_deg,roll_deg,first_rise_C0_sample,first_rise_B_sample,first_rise_delay_us,first_rise_status,full_window_delay_us,early_full_delta_us,stable_neighbors,stable_median_us,stable_mad_us,robust_candidate,robust_reason,reference_label,axis_profile,empirical_axis_score,empirical_sector,exceeds_ideal_air_limit,ideal_air_excess_us,acoustic_repeatable,first_onset_agree\n");
            imuOut.write("sensor_event_boottime_ns,type,value0,value1,value2,value3\n");
            if(continuousOutput!=null)continuousOutput.write(
                "center_frame,audio_boottime_ns,audio_time_status,pose_status,pose_time_ns,qw,qx,qy,qz,yaw_deg,pitch_deg,roll_deg,level_dbfs,level_diff_db,spectral_flatness,dominant_band,raw_delay_us,smoothed_delay_us,quality_status,reliable,valid_bands,agreeing_bands,empirical_axis_score,empirical_axis_sector,reference_label,low_delay_us,low_peak,low_contrast,low_reason,mid_delay_us,mid_peak,mid_contrast,mid_reason,high_delay_us,high_peak,high_contrast,high_reason,adaptive_delay_us,adaptive_peak,adaptive_contrast,adaptive_reason,adaptive_low_hz,adaptive_high_hz,confidence_class,temporal_stable,temporal_neighbors,temporal_median_us,temporal_mad_us,motion_status,rotation_deg_600ms,rotation_speed_deg_s,max_geometric_change_us,pose_adjusted_stable,axis_preview_status,low_coherence,low_peak_count,low_alt_delay_us,low_alt_peak_ratio,mid_coherence,mid_peak_count,mid_alt_delay_us,mid_alt_peak_ratio,high_coherence,high_peak_count,high_alt_delay_us,high_alt_peak_ratio,adaptive_coherence,adaptive_peak_count,adaptive_alt_delay_us,adaptive_alt_peak_ratio\n");
            if(continuousMode)writeLog("MODO CONTÍNUO 0.4.1: três bandas fixas + banda adaptativa e histórico temporal.");
            writeLog("v0.3.2: dois níveis: qualidade de TDOA + repetibilidade em pose semelhante.");
            writeLog("Referência rotulada pelo usuário: "+knownReference);
            writeLog("Perfil 1D piloto: MIC_INFERIOR=-434,7; CENTRO=-2,8; MIC_SUPERIOR=+472,1 us.");
            writeLog("QUALIFICADO = filtros heurísticos; NÃO equivale a direção correta.");
            writeLog("Atraso positivo: C1 chega depois de C0; não há azimute calibrado.");
            ar.startRecording();
            if (ar.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new IOException("Gravação não iniciou");
            try {
                java.util.List<MicrophoneInfo> active = ar.getActiveMicrophones();
                writeLog("Microfones ativos: " + active.size());
                for (int i = 0; i < active.size(); i++)
                    writeLog("[Ativo " + i + "] " + micDetails(active.get(i)));
            } catch(Exception e) { writeLog("Sem mapeamento dos microfones ativos: " + e); }
            runOnUiThread(() -> getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            short[] audio = new short[2048 * actualChannels]; // short buffers reduce event latency
            AudioTimestamp ts = new AudioTimestamp();
            long began = android.os.SystemClock.elapsedRealtimeNanos();
            long lastUi = 0;
            while (running && android.os.SystemClock.elapsedRealtimeNanos()-began <
                    (continuousMode?120_000_000_000L:30_000_000_000L)) {
                int n=ar.read(audio,0,audio.length,AudioRecord.READ_BLOCKING);
                if(n<0)throw new IOException("AudioRecord.read = "+n);
                n-=n%actualChannels;
                if(n==0)continue;
                wav.write(audio,n);
                frames += n/actualChannels;
                long nowBoot=android.os.SystemClock.elapsedRealtimeNanos();
                int rc=ar.getTimestamp(ts,AudioTimestamp.TIMEBASE_BOOTTIME);
                if(rc==AudioRecord.SUCCESS) {
                    audioClock.update(ts.framePosition,ts.nanoTime,frames,nowBoot);
                } else audioClock.invalidate();

                if(tracker!=null)tracker.feed(audio,n,window -> {
                    continuousCount[0]++;
                    if(window.reliable)continuousReliable[0]++;
                    if(window.temporalStable&&!window.reliable)continuousStable[0]++;
                    if(Double.isFinite(window.rawDelayUs))continuousCandidates[0]++;
                    AudioClock.Mapping mapped=audioClock.timeForFrame(window.centerFrame);
                    PoseHistory.Pose pose=mapped.valid?motion.poseAt(mapped.timeNs):motion.poseAt(-1);
                    MotionEvidence.Result motionResult=motionEvidence.observe(
                        mapped.valid?mapped.timeNs:-1,pose.valid?pose.q:null,
                        window.rawDelayUs,Double.isFinite(window.rawDelayUs));
                    if(motionResult.moving && motionResult.plausible)motionPlausible[0]++;
                    if(motionResult.moving && !motionResult.plausible)motionRejected[0]++;
                    if(motionResult.stationary && pose.valid && mapped.valid){
                        if(stationarySinceNs[0]<0)stationarySinceNs[0]=mapped.timeNs;
                    } else stationarySinceNs[0]=-1;
                    boolean stationaryMature=stationarySinceNs[0]>0 && mapped.valid &&
                        mapped.timeNs-stationarySinceNs[0]>=700_000_000L;
                    boolean poseAdjustedStable=window.temporalStable &&
                        stationaryMature && motionResult.stationary;
                    String previewStatus=window.reliable && pose.valid && mapped.valid?
                        "MULTIBANDA_EIXO":poseAdjustedStable?"PARADO_ESTAVEL":
                        motionResult.moving&&motionResult.plausible?
                            "ROTACAO_EIXO_INCERTO":"SEM_DIRECAO_VALIDADA";
                    EmpiricalAxis.Axis axis=Double.isFinite(window.rawDelayUs)?
                        EmpiricalAxis.estimate(window.rawDelayUs):null;
                    try {
                        continuousOutput.write(String.format(Locale.US,
                            "%d,%d,%s,%s,%d,%.8f,%.8f,%.8f,%.8f,%.3f,%.3f,%.3f,%.3f,%.3f,%.5f,%s,%.4f,%.4f,%s,%d,%d,%d,%.5f,%s,%s",
                            window.centerFrame,mapped.valid?mapped.timeNs:-1,mapped.status,
                            pose.quality,pose.timeNs,pose.q[0],pose.q[1],pose.q[2],pose.q[3],
                            pose.yaw,pose.pitch,pose.roll,window.levelDb,window.levelDifferenceDb,
                            window.flatness,window.dominantBand,window.rawDelayUs,window.smoothedDelayUs,
                            window.status,window.reliable?1:0,window.validBands,
                            window.agreeingBands,axis!=null?axis.rawScore:Double.NaN,
                            axis!=null?axis.sector:"SEM_ESTIMATIVA",knownReference));
                        for(ContinuousTracker.Band band:window.bands)
                            continuousOutput.write(String.format(Locale.US,",%.4f,%.4f,%.4f,%s",
                                band.delayUs,band.peakRatio,band.contrast,band.reason));
                        continuousOutput.write(String.format(Locale.US,
                            ",%.2f,%.2f,%s,%d,%d,%.3f,%.3f",
                            window.adaptiveLowHz,window.adaptiveHighHz,
                            window.confidenceClass,window.temporalStable?1:0,
                            window.temporalNeighbors,window.temporalMedianUs,
                            window.temporalMadUs));
                        continuousOutput.write(String.format(Locale.US,
                            ",%s,%.4f,%.4f,%.4f,%d,%s",
                            motionResult.status,motionResult.relativeDegrees,
                            motionResult.speedDegPerSec,motionResult.maxGeometricChangeUs,
                            poseAdjustedStable?1:0,previewStatus));
                        for(ContinuousTracker.Band band:window.bands)
                            continuousOutput.write(String.format(Locale.US,
                                ",%.5f,%d,%.4f,%.4f",
                                band.spectralCoherence,band.candidatePeaks,
                                band.alternateDelayUs,band.alternatePeakRatio));
                        continuousOutput.newLine();
                    } catch(IOException e){throw new IllegalStateException("CSV contínuo: "+e.getMessage(),e);}
                    long uiTime=android.os.SystemClock.elapsedRealtimeNanos();
                    if(uiTime-lastContinuousUi[0]>180_000_000L){
                        lastContinuousUi[0]=uiTime;
                        StringBuilder output=new StringBuilder(window.summary());
                        if(mapped.valid && pose.valid)
                            output.append(String.format(Locale.US,
                                "\nIMU: yaw %+.1f | pitch %+.1f | roll %+.1f",pose.yaw,pose.pitch,pose.roll));
                        if((window.reliable||poseAdjustedStable) && axis!=null){
                            output.append("\n").append(axis.display());
                            output.append("\nPERFIL PILOTO 1D; não é ângulo 3D.");
                            if(poseAdjustedStable&&!window.reliable)
                                output.append("\nApenas estabilidade com celular parado.");
                            if(motionResult.moving)
                                output.append("\nRotação detectada: direção 1D provisória.");
                        }
                        output.append(String.format(Locale.US,
                            "\nMovimento: %s | %.1f° | %.1f°/s",
                            motionResult.status,motionResult.relativeDegrees,
                            motionResult.speedDegPerSec));
                        final String currentView=output.toString();
                        final EmpiricalAxis.Axis currentAxis=axis;
                        final boolean displayAxis=currentAxis!=null &&
                            !"SEM_DIRECAO_VALIDADA".equals(previewStatus) &&
                            !window.clipped &&
                            Double.isFinite(window.rawDelayUs);
                        double mad=window.temporalMadUs;
                        final double uncertaintyScore=Double.isFinite(mad)?
                            Math.min(.5,Math.max(.045,3*mad/454.0)):.22;
                        final String viewStatus=previewStatus;
                        runOnUiThread(()->{
                            live.setText(currentView);
                            axisIndicator.show(displayAxis?currentAxis.rawScore:Double.NaN,
                                uncertaintyScore,viewStatus,
                                !("MULTIBANDA_EIXO".equals(viewStatus) && !motionResult.moving));
                        });
                    }
                });
                if(!continuousMode)detector.feed(audio,n,event -> {
                    AudioClock.Mapping mapping=audioClock.timeForFrame(event.onsetFrame);
                    PoseHistory.Pose pose=mapping.valid ?
                        motion.poseAt(mapping.timeNs) : motion.poseAt(-1);
                    SpatialDsp.Estimate[] early =
                        SpatialDsp.analyzeEarly(event.pcm,event.pcm.length,actualChannels,actualRate);
                    SpatialDsp.Estimate[] full =
                        SpatialDsp.analyze(event.pcm,event.pcm.length,actualChannels,actualRate);
                    processedEvents[0]++;
                    if(mapping.valid && pose.valid)syncedEvents[0]++;
                    StringBuilder details=new StringBuilder();
                    details.append("Estalo ").append(processedEvents[0]).append(" | ")
                        .append(mapping.status).append(" | ").append(pose.quality).append('\n');
                    for(int k=0;k<early.length;k++){
                        SpatialDsp.Estimate est=early[k], all=full[k];
                        if(est.valid)candidates[0]++;
                        OnsetInspector.Pair first=OnsetInspector.examine(
                            event.pcm,actualChannels,est.channel,actualRate);
                        double windowDelta=Math.abs(est.delayUs-all.delayUs);
                        boolean windowAgree=Double.isFinite(windowDelta) && windowDelta<=125.0;
                        // First-rise is a SECONDARY OBSERVATION, not a veto:
                        // threshold crossings differ across processed Android channels.
                        boolean firstAgree=first.usable() &&
                            Math.abs(first.delayUs-est.delayUs)<=150.0;
                        boolean acousticallyClean=est.valid && windowAgree;
                        DelayStability.Stats st=stability[k].observe(
                            mapping.valid?mapping.timeNs:-1,pose,est.delayUs,acousticallyClean);
                        boolean qualified=acousticallyClean && !st.outlier;
                        boolean repeated=qualified && st.count>=3 &&
                            Double.isFinite(st.madUs) && st.madUs<=35.0 &&
                            Math.abs(est.delayUs-st.medianUs)<=Math.max(35.0,4.0*1.4826*st.madUs);
                        EmpiricalAxis.Axis axis=EmpiricalAxis.estimate(est.delayUs);
                        String robustReason;
                        if(!est.valid)robustReason=est.reason;
                        else if(!windowAgree)robustReason="JANELAS_DIVERGEM";
                        else if(st.outlier)robustReason="DISPERSAO_ELEVADA";
                        else robustReason="TDOA_CANDIDATO";
                        if(qualified)robustCount[0]++;
                        if(repeated)repeatableCount[0]++;
                        try{
                            // Preserve v0.3 columns, append v0.3.1 diagnostics.
                            eventOutput.write(String.format(Locale.US,
                                "%d,%d,%d,%s,%d,%d,%d,%.5f,%.4f,%.5f,%.5f,%.3f,%.3f,%.7f,%.5f,%d,%s,%.2f,%s,%d,%.8f,%.8f,%.8f,%.8f,%.3f,%.3f,%.3f",
                                event.onsetFrame,event.windowStartFrame,
                                mapping.valid?mapping.timeNs:-1,mapping.status,
                                audioClock.anchorFrame(),audioClock.anchorTimeNs(),
                                est.channel,est.delaySamples,est.delayUs,est.peak,
                                est.contrast,est.level0Db,est.levelBDb,est.clippingFraction,
                                est.difference,est.valid?1:0,est.reason,event.triggerRatio,
                                pose.quality,pose.timeNs,pose.q[0],pose.q[1],pose.q[2],pose.q[3],
                                pose.yaw,pose.pitch,pose.roll));
                            eventOutput.write(String.format(Locale.US,
                                ",%d,%d,%.4f,%s,%.4f,%.4f,%d,%.4f,%.4f,%d,%s,%s,%s,%.5f,%s,%d,%.5f,%d,%d\n",
                                first.onset0,first.onsetB,first.delayUs,first.quality,
                                all.delayUs,windowDelta,st.count,st.medianUs,st.madUs,
                                qualified?1:0,robustReason,knownReference,EmpiricalAxis.PROFILE,
                                axis.rawScore,axis.sector,axis.beyondAirLimit?1:0,
                                Math.max(0,Math.abs(est.delayUs)-EmpiricalAxis.THEORETICAL_AIR_LIMIT_US),
                                repeated?1:0,firstAgree?1:0));
                        }catch(IOException e){throw new IllegalStateException("Falha CSV: "+e.getMessage(),e);}
                        details.append(String.format(Locale.US,
                            "%+.1f us | %s | reps=%d MAD=%.1f us | %s\n",
                            est.delayUs,qualified?"TDOA_OK":robustReason,
                            st.count,st.madUs,repeated?"REPETIVEL":"NAO_VALIDADO"));
                        if(qualified)details.append(axis.display()).append("\n");
                    }
                    final String screen=details.toString();
                    runOnUiThread(() -> live.setText(screen));
                });
                String imuLine;
                while((imuLine=motion.pollLine())!=null){
                    motionOutput.write(imuLine);motionOutput.newLine();
                }
                if(nowBoot-lastUi>1_000_000_000L) {
                    lastUi=nowBoot;
                    long seconds=frames/actualRate;
                    final String status=String.format(Locale.US,
                        "t=%ds: %d eventos, %d GCC, %d TDOA_OK, %d repetiveis, %d IMU | Timestamp OK=%d, rejeitado=%d",
                        seconds,processedEvents[0],candidates[0],robustCount[0],repeatableCount[0],syncedEvents[0],
                        audioClock.accepted(),audioClock.rejected());
                    writeLog(status);
                }
            }
            if(continuousMode)writeLog("Janelas contínuas="+continuousCount[0]+
                ", multibanda OK="+continuousReliable[0]+
                ", estabilidade temporal somente="+continuousStable[0]+
                ", atrasos candidatos="+continuousCandidates[0]+
                ", rotações plausíveis="+motionPlausible[0]+
                ", rotações incompatíveis="+motionRejected[0]+
                "; arquivo="+continuousFile.getName());
            writeLog("Gravados "+frames+" frames; "+wavFile.getName());
            writeLog("Estalos detectados="+detector.detectedCount()+", analisados="
                +processedEvents[0]+", descartados por fila="+detector.overflowCount()
                +", GCC candidatos="+candidates[0]+", TDOA_OK="+robustCount[0]+", repetiveis="+repeatableCount[0]+", sincronizados IMU="+syncedEvents[0]+", blocos de eco suprimidos="+detector.suppressedBlocks());
            writeLog("AudioTimestamp aceitos="+clock.accepted()+", rejeitados="
                +clock.rejected()+"; ausências também podem ocorrer.");
            writeLog("Arquivos: "+csvFile.getName()+" e "+imuFile.getName());
        } catch(Exception e) {
            writeLog("ERRO de captura: "+e);
            runOnUiThread(() -> live.setText("Falha: "+e.getMessage()));
        } finally {
            running=false;
            motion.stop();
            if(ar!=null){ try{ ar.stop(); }catch(Exception ignored){} ar.release(); }
            current=null;
            if(imuOut!=null){
                try{
                    String imuLine;
                    while((imuLine=motion.pollLine())!=null){
                        imuOut.write(imuLine);imuOut.newLine();
                    }
                }catch(Exception ignored){}
            }
            if(csvOut!=null)try{csvOut.close();}catch(Exception e){writeLog("Falha CSV: "+e);}
            if(imuOut!=null)try{imuOut.close();}catch(Exception e){writeLog("Falha IMU: "+e);}
            if(continuousOut!=null)try{continuousOut.close();}catch(Exception e){writeLog("Falha CSV contínuo: "+e);}
            if(wav!=null)try{wav.close();}catch(Exception e){writeLog("Falha WAV: "+e);}
            runOnUiThread(() -> {
                startButton.setEnabled(true);stopButton.setEnabled(false);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }
    private void copyReport() {
        ClipboardManager clip = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setPrimaryClip(ClipData.newPlainText("diagnostico", reportText()));
        Toast.makeText(this, "Relatório copiado. Cole no ChatGPT.", Toast.LENGTH_LONG).show();
    }
    private void export(int what) {
        if (running) { Toast.makeText(this, "Pare a gravação antes de exportar.", Toast.LENGTH_SHORT).show(); return; }
        if ((what == 102 && lastWav == null) || (what == 103 && lastCsv == null) ||
            (what == 104 && lastImu == null) || (what == 105 && lastContinuousCsv == null)) {
            Toast.makeText(this, "Grave áudio antes de exportar WAV.", Toast.LENGTH_LONG).show(); return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(what == 102 ? "audio/wav" : what == 101 ? "text/plain" : "text/csv");
        String filename = what == 101 ? "diagnostico_s25.txt" :
            what == 102 ? lastWav.getName() : what == 103 ? lastCsv.getName() :
                what == 104 ? lastImu.getName() : lastContinuousCsv.getName();
        i.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(i, what);
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException("Destino indisponível");
            if (code == 101) out.write(reportText().getBytes("UTF-8"));
            else if ((code == 102 && lastWav != null) ||
                     (code == 103 && lastCsv != null) ||
                     (code == 104 && lastImu != null) ||
                     (code == 105 && lastContinuousCsv != null)) {
                File exporting = code == 102 ? lastWav : code == 103 ? lastCsv :
                    code == 104 ? lastImu : lastContinuousCsv;
                try (InputStream in = new FileInputStream(exporting)) {
                    byte[] buf = new byte[8192];
                    int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
            }
            Toast.makeText(this, "Arquivo exportado.", Toast.LENGTH_LONG).show();
        } catch (Exception e) { writeLog("ERRO exportação: " + e); }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 10) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) listMicrophones();
            else writeLog("Permissão negada.");
        }
    }
    @Override protected void onStop() {
        running = false;
        super.onStop();
    }
}
