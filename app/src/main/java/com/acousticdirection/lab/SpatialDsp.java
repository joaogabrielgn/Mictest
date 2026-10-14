package com.acousticdirection.lab;

import java.util.Locale;

/**
 * v0.2: experimental GCC-PHAT for synchronous multichannel PCM.
 * Lag convention: positive microseconds = channel B arrives AFTER channel 0.
 * No azimuth/elevation estimation, channel-to-physical-mic assignment or calibration.
 */
public final class SpatialDsp {
    private static final int FFT_N = 2048;
    private static final double DEFAULT_SPACING_METERS = 0.1426;
    private static final double SOUND_SPEED_M_S = 343.0;

    private SpatialDsp() {}

    public static final class Estimate {
        public final int channel;
        public final double delaySamples, delayUs, level0Db, levelBDb;
        public final double peak, contrast, clippingFraction, difference;
        public final boolean valid;
        public final String reason;
        Estimate(int channel, double lag, double delayUs, double level0Db, double levelBDb,
                 double peak, double contrast, double clip, double difference, boolean valid, String reason) {
            this.channel = channel; this.delaySamples = lag; this.delayUs = delayUs;
            this.level0Db = level0Db; this.levelBDb = levelBDb;
            this.peak = peak; this.contrast = contrast; this.clippingFraction = clip;
            this.difference = difference; this.valid = valid; this.reason = reason;
        }
        public String display() {
            return String.format(Locale.US,
                "C0/C%d: %+.1f us | pico %.3f | contraste %.2f | dL %+.1f dB | clip %.1f%% | %s",
                channel, delayUs, peak, contrast, levelBDb-level0Db,
                clippingFraction * 100.0, valid ? "CANDIDATO" : reason);
        }
        public String csv(long frames, long hostNs, MotionTracker.Snapshot s) {
            return String.format(Locale.US,
                "%d,%d,%d,%.4f,%.3f,%.6f,%.5f,%.3f,%.3f,%.6f,%.4f,%s,%s,%d,%.2f,%.2f,%.2f",
                frames, hostNs, channel, delaySamples, delayUs, peak, contrast,
                level0Db, levelBDb, clippingFraction, difference,
                valid ? "1":"0", reason, s.eventNs, s.yaw, s.pitch, s.roll);
        }
    }

    /**
     * For a transient at about frame 256 in the detector window, emphasize the
     * first ~16 ms after the onset, reducing the impact of late reverberation.
     * Retain the same FFT size and the same 2048 frame PCM layout.
     */
    public static Estimate[] analyzeEarly(short[] pcm, int count, int channels, int rate) {
        if (channels < 2 || count < FFT_N * channels) return new Estimate[0];
        short[] early = new short[FFT_N * channels];
        int keep = 1024; // ~21ms total, including ~5ms pre-trigger, at 48kHz
        System.arraycopy(pcm,0,early,0,keep*channels);
        return analyze(early,early.length,channels,rate);
    }

    /** Choose the strongest 2048-frame part of the current read; estimate C0 vs C1... */
    public static Estimate[] analyze(short[] interleaved, int count, int channels, int rate) {
        if (channels < 2 || rate <= 0 || count / channels < FFT_N) return new Estimate[0];
        int frames = count / channels;
        int blocks = frames / FFT_N;
        double maxEnergy = -1.0;
        int selected = 0;
        for (int block = 0; block < blocks; block++) {
            double e = 0;
            int origin = block * FFT_N * channels;
            for (int i = 0; i < FFT_N; i++) {
                for (int ch = 0; ch < Math.min(channels, 2); ch++) {
                    double sample = interleaved[origin + i*channels + ch] / 32768.0;
                    e += sample * sample;
                }
            }
            if (e > maxEnergy) { maxEnergy = e; selected = block; }
        }
        int origin = selected * FFT_N * channels;
        double[][] samples = new double[channels][FFT_N];
        double[] rms = new double[channels];
        double[] clip = new double[channels];
        for (int ch = 0; ch < channels; ch++) {
            double power = 0, clipped = 0;
            for (int i = 0; i < FFT_N; i++) {
                double v = interleaved[origin + i*channels + ch] / 32768.0;
                samples[ch][i] = v;
                power += v * v;
                if (Math.abs(v) >= .995) clipped++;
            }
            rms[ch] = Math.sqrt(power / FFT_N);
            clip[ch] = clipped / FFT_N;
        }
        int maxLag = Math.min(64, (int)Math.ceil(rate * DEFAULT_SPACING_METERS / SOUND_SPEED_M_S) + 4);
        Estimate[] estimates = new Estimate[channels - 1];
        for (int ch = 1; ch < channels; ch++) {
            estimates[ch-1] = estimatePair(samples[0], samples[ch], rms[0], rms[ch],
                Math.max(clip[0],clip[ch]), ch, rate, maxLag);
        }
        return estimates;
    }

    /** Pure Java method for synthetic tests; b[n] = a[n-k] => positive reported delay k. */
    static Estimate estimatePair(double[] a, double[] b, double rmsA, double rmsB,
                                 double clip, int channel, int rate, int maxLag) {
        double[] ar = new double[FFT_N], ai = new double[FFT_N];
        double[] br = new double[FFT_N], bi = new double[FFT_N];
        double meanA = 0, meanB = 0;
        for (int i = 0; i < FFT_N; i++) { meanA += a[i]; meanB += b[i]; }
        meanA /= FFT_N; meanB /= FFT_N;
        // Hann and first-difference pre-emphasis suppress DC and diffuse low frequency noise.
        double previousA = 0, previousB = 0;
        for (int i = 0; i < FFT_N; i++) {
            double va = a[i] - meanA, vb = b[i] - meanB;
            // A flat-top window preserves early transients at frame ~256; the
            // old Hann window strongly attenuated that region (~14% amplitude).
            double h = i<128 ? 0.5-0.5*Math.cos(Math.PI*i/128.0)
                 : i>=FFT_N-128 ? 0.5-0.5*Math.cos(Math.PI*(FFT_N-1-i)/127.0)
                 : 1.0;
            ar[i] = (va - 0.96*previousA)*h;
            br[i] = (vb - 0.96*previousB)*h;
            previousA = va; previousB = vb;
        }
        fft(ar, ai, false); fft(br, bi, false);
        // Cross-power X * conj(Y). Exclude bins outside 450-9000Hz.
        for (int k = 0; k < FFT_N; k++) {
            double frequency = ((k <= FFT_N/2) ? k : (FFT_N-k)) * (double)rate / FFT_N;
            if (frequency < 450.0 || frequency > Math.min(9000.0, 0.45*rate)) {
                ar[k] = 0; ai[k] = 0;
                continue;
            }
            double re = ar[k]*br[k] + ai[k]*bi[k];
            double im = ai[k]*br[k] - ar[k]*bi[k];
            double amp = Math.hypot(re,im);
            // PHAT with regularization: avoid amplifying near-zero spectral bins.
            double weight = 1.0 / (amp + 1e-10);
            ar[k] = re * weight; ai[k] = im * weight;
        }
        fft(ar, ai, true);
        maxLag = Math.min(Math.max(maxLag, 1), FFT_N/4);
        double best = -Double.MAX_VALUE;
        int bestLag = 0;
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double v = ar[(lag + FFT_N) % FFT_N];
            if (v > best) { best = v; bestLag = lag; }
        }
        double second = 0, energy = 0;
        int checked = 0;
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double v = Math.abs(ar[(lag+FFT_N)%FFT_N]);
            energy += v*v; checked++;
            if (Math.abs(lag-bestLag) > 2) second = Math.max(second, v);
        }
        // A contrast and a standardized peak metric; neither is a probability.
        double contrast = Math.max(0.0, (best-second)/Math.max(best,1e-12));
        double background = Math.sqrt(energy/checked);
        double peak = best / Math.max(background,1e-12);
        double subSample = 0;
        if (bestLag > -maxLag && bestLag < maxLag) {
            double left = ar[(bestLag - 1 + FFT_N) % FFT_N];
            double right = ar[(bestLag + 1 + FFT_N) % FFT_N];
            double denominator = left - 2*best + right;
            if (Math.abs(denominator) > 1e-12) {
                subSample = 0.5*(left-right)/denominator;
                subSample = Math.max(-0.5, Math.min(0.5, subSample));
            }
        }
        double normalizedDifference = 0;
        for (int i=0;i<FFT_N;i++) {
            double d = a[i]-b[i];
            normalizedDifference += d*d;
        }
        normalizedDifference = Math.sqrt(normalizedDifference/FFT_N) /
            Math.max(Math.max(rmsA,rmsB),1e-10);
        String reason="OK";
        if (rmsA < .0008 || rmsB < .0008) reason="SINAL_FRACO";
        else if (clip > 0.0) reason="CLIPPING";
        else if (normalizedDifference < .015) reason="CANAIS_SIMILARES";
        else if (bestLag == -maxLag || bestLag == maxLag) reason="LIMITE_GEOMETRICO";
        else if (peak < 2.2 || contrast < .09) reason="PICO_AMBIGUO";
        double lag = -(bestLag + subSample);
        // The physical constraint is applied to the measured lag, not just the FFT search range.
        // Allow 1.25 samples of tolerance for interpolation / rate quantization. This is
        // provisional until the Samsung processed-channel fixed offset is calibrated.
        double physicalUs = DEFAULT_SPACING_METERS / SOUND_SPEED_M_S * 1.0e6;
        double measuredUs = Math.abs(lag * 1.0e6 / rate);
        if ("OK".equals(reason) && measuredUs > physicalUs + 1.25e6/rate)
            reason = "FORA_LIMITE_FISICO";
        return new Estimate(channel, lag, lag * 1e6 / rate,
            20*Math.log10(Math.max(rmsA,1e-10)),20*Math.log10(Math.max(rmsB,1e-10)),
            peak, contrast, clip, normalizedDifference, "OK".equals(reason), reason);
    }

    /** In-place radix-2 FFT, forward e^(-i*w*t). */
    private static void fft(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        for (int i=1,j=0;i<n;i++) {
            int bit=n>>1;
            while ((j & bit)!=0) { j ^= bit; bit >>= 1; }
            j ^= bit;
            if (i<j) {
                double t=re[i];re[i]=re[j];re[j]=t;
                t=im[i];im[i]=im[j];im[j]=t;
            }
        }
        for(int size=2;size<=n;size<<=1) {
            double angle = (inverse ? 2.0 : -2.0)*Math.PI/size;
            double wrStep=Math.cos(angle), wiStep=Math.sin(angle);
            for(int start=0;start<n;start+=size) {
                double wr=1,wi=0;
                for(int j=0;j<size/2;j++) {
                    int i0=start+j,i1=i0+size/2;
                    double tr=wr*re[i1]-wi*im[i1], ti=wr*im[i1]+wi*re[i1];
                    re[i1]=re[i0]-tr;im[i1]=im[i0]-ti;
                    re[i0]+=tr;im[i0]+=ti;
                    double tmp=wr*wrStep-wi*wiStep;
                    wi=wr*wiStep+wi*wrStep;wr=tmp;
                }
            }
        }
        if (inverse) for(int i=0;i<n;i++){re[i]/=n; im[i]/=n;}
    }
}
