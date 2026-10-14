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
    private Spinner sourceSpinner, channelSpinner, rateSpinner;
    private Button startButton, stopButton;
    private TextView live, log;
    private AudioManager manager;
    private volatile boolean running = false;
    private volatile AudioRecord current;
    private File lastWav;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(Color.rgb(12, 24, 36));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15), dp(24), dp(15), dp(20));
        scroller.addView(root);
        setContentView(scroller);
        title(root, "ACOUSTIC DIRECTION LAB", 21, 0xFF62E0C3);
        title(root, "Diagnóstico de microfones / v0.1", 14, Color.LTGRAY);
        sourceSpinner = selector(root, "Fonte", sourceNames, 0);
        channelSpinner = selector(root, "Número de canais", new String[]{"1 canal", "2 canais", "3 canais", "4 canais"}, 1);
        rateSpinner = selector(root, "Frequência", new String[]{"48000 Hz", "96000 Hz", "44100 Hz"}, 0);
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
        title(root, "Análise preliminar", 17, Color.WHITE);
        live = new TextView(this);
        live.setTextColor(0xFF9BF0DF); live.setTextSize(13);
        live.setTypeface(Typeface.MONOSPACE);
        live.setPadding(dp(8), dp(8), dp(8), dp(8));
        live.setBackgroundColor(0xFF1B3844);
        live.setText("Aguardando teste...");
        root.addView(live);
        title(root, "Relatório técnico", 17, Color.WHITE);
        log = new TextView(this);
        log.setTextColor(Color.WHITE); log.setTextSize(12);
        log.setTypeface(Typeface.MONOSPACE); log.setTextIsSelectable(true);
        root.addView(log);
        writeLog("Acoustic Direction Lab v0.1");
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
        running = true; startButton.setEnabled(false); stopButton.setEnabled(true);
        new Thread(() -> record(sourceIndex, channels, rate), "CapturePCM").start();
    }
    private void record(int sourceIndex, int channelCount, int rate) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        AudioRecord ar = null;
        WavWriter wav = null;
        long frames = 0;
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
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("AudioRecord não inicializado");
            current = ar;
            final int actualChannels = ar.getChannelCount();
            final int actualRate = ar.getSampleRate();
            writeLog("\n=== TESTE: " + sourceNames[sourceIndex] + ", pedido="
                + channelCount + "ch / " + rate + "Hz ===");
            writeLog("Captura resultante: " + actualChannels + "ch / " + actualRate
                + "Hz; session=" + ar.getAudioSessionId());
            File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (dir == null) dir = getFilesDir();
            dir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            lastWav = new File(dir, "mic_" + timestamp + "_" + sourceNames[sourceIndex]
                + "_" + actualChannels + "ch.wav");
            wav = new WavWriter(lastWav, actualChannels, actualRate);
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
            short[] audio = new short[4096 * actualChannels];
            long began = System.nanoTime(), updated = 0;
            while (running && System.nanoTime() - began < 15_000_000_000L) {
                int n = ar.read(audio, 0, audio.length, AudioRecord.READ_BLOCKING);
                if (n < 0) throw new IOException("AudioRecord.read = " + n);
                n -= n % actualChannels;
                if (n <= 0) continue;
                wav.write(audio, n);
                frames += n / actualChannels;
                long now = System.nanoTime();
                if (now - updated > 300_000_000L) {
                    updated = now;
                    final String text = String.format(Locale.US,
                        "%.1f s, %d canais, %d Hz\n", frames / (double)actualRate,
                        actualChannels, actualRate)
                        + AudioAnalysis.inspect(audio, n, actualChannels, actualRate);
                    runOnUiThread(() -> live.setText(text));
                }
            }
            writeLog("Gravados " + frames + " frames; arquivo " + lastWav.getName());
        } catch (Exception e) {
            writeLog("ERRO de captura: " + e);
            runOnUiThread(() -> live.setText("Falha: " + e.getMessage()));
        } finally {
            running = false;
            if (ar != null) {
                try { ar.stop(); } catch (Exception ignored) {}
                ar.release();
            }
            current = null;
            if (wav != null) try { wav.close(); } catch (Exception e) { writeLog("Falha WAV: " + e); }
            runOnUiThread(() -> {
                startButton.setEnabled(true); stopButton.setEnabled(false);
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
        if (what == 102 && lastWav == null) {
            Toast.makeText(this, "Grave áudio antes de exportar WAV.", Toast.LENGTH_LONG).show(); return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(what == 101 ? "text/plain" : "audio/wav");
        i.putExtra(Intent.EXTRA_TITLE, what == 101 ? "diagnostico_s25.txt" : lastWav.getName());
        startActivityForResult(i, what);
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException("Destino indisponível");
            if (code == 101) out.write(reportText().getBytes("UTF-8"));
            else if (code == 102 && lastWav != null) {
                try (InputStream in = new FileInputStream(lastWav)) {
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
