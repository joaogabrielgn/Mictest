package com.acousticdirection.lab;

import java.util.Locale;

public final class AudioAnalysis {
    private AudioAnalysis() {}
    public static String inspect(short[] pcm, int count, int channels, int rate) {
        int frames = count / channels;
        if (frames < 180) return "Aguardando amostras...";
        int n = Math.min(frames, 1400);
        double[][] samples = new double[channels][n];
        double[] rms = new double[channels];
        StringBuilder s = new StringBuilder();
        for (int ch = 0; ch < channels; ch++) {
            double power = 0, previous = 0;
            for (int i = 0; i < n; i++) {
                double v = pcm[i * channels + ch] / 32768.0;
                power += v * v;
                samples[ch][i] = v - 0.95 * previous;
                previous = v;
            }
            rms[ch] = Math.sqrt(power / n);
            s.append(String.format(Locale.US, "C%d RMS: %.1f dBFS\n", ch,
                20 * Math.log10(Math.max(rms[ch], 1e-10))));
        }
        for (int ch = 1; ch < channels; ch++) {
            int maxLag = Math.min(32, n / 5), bestLag = 0;
            double best = -2.0, difference = 0;
            for (int lag = -maxLag; lag <= maxLag; lag++) {
                double corr = 0, aPower = 0, bPower = 0;
                for (int i = maxLag; i < n - maxLag; i++) {
                    double a = samples[0][i], b = samples[ch][i + lag];
                    corr += a * b; aPower += a * a; bPower += b * b;
                }
                double score = corr / Math.sqrt(Math.max(aPower * bPower, 1e-18));
                if (score > best) { best = score; bestLag = lag; }
            }
            for (int i = 0; i < n; i++) {
                double d = samples[0][i] - samples[ch][i];
                difference += d * d;
            }
            double rel = Math.sqrt(difference / n) / Math.max(Math.max(rms[0], rms[ch]), 1e-9);
            s.append(String.format(Locale.US,
                "C0 x C%d: delay=%+.1f us, corr=%.2f, relative difference=%.4f\n",
                ch, bestLag * 1000000.0 / rate, best, rel));
            if (rel < 0.005) s.append("AVISO: canais possivelmente duplicados.\n");
            if (best < 0.2) s.append("AVISO: atraso incerto.\n");
        }
        return s.toString();
    }
}
