package com.acousticdirection.lab;

/**
 * Heuristic eligibility score; not a probability of correct location.
 * Uses spectral quality, peak uniqueness, signal level, interpolation gap,
 * motion/translation, and the ARCore frame-clock gate.
 */
public final class PairQuality {
    public static final class Verdict {
        public final double score;
        public final String reason;
        public final boolean accepted;
        Verdict(double value,boolean accepted,String reason){
            score=value;this.accepted=accepted;this.reason=reason;
        }
    }
    public static Verdict evaluate(double coherence,double peakRatio,
            double altPeakRatio,double levelDbfs,boolean clipped,
            double maxInterpolationGapMs,double angularSpeed,
            double translationMeters,boolean anchor,
            boolean clocksCompatible,boolean hasAmbiguousTone){
        if(!clocksCompatible)return new Verdict(0,false,"RELOGIOS_NAO_VERIFICADOS");
        if(!Double.isFinite(maxInterpolationGapMs)||maxInterpolationGapMs>70)
            return new Verdict(0,false,"DESALINHAMENTO_CAMERA_AUDIO");
        if(clipped||levelDbfs<-52||levelDbfs>-3)
            return new Verdict(0,false,"NIVEL_OU_SATURACAO");
        if(!anchor && translationMeters>.15)
            return new Verdict(0,false,"TRANSLACAO_SEM_ANCORA");
        if(angularSpeed>32)return new Verdict(0,false,"MOVIMENTO_RAPIDO");
        if(!Double.isFinite(coherence)||coherence<.14)return new Verdict(0,false,"COERENCIA_BAIXA");
        if(!Double.isFinite(peakRatio)||peakRatio<2.3)
            return new Verdict(0,false,"PICO_FRACO");
        if(!Double.isFinite(altPeakRatio)||altPeakRatio>.94)
            return new Verdict(0,false,"PICOS_AMBIGUOS");
        // Limited effect of volume: we seek direction, not source loudness.
        double c=clamp01((coherence-.12)/.70);
        double peak=clamp01((peakRatio-2)/5);
        double uniqueness=clamp01((.98-altPeakRatio)/.65);
        double level=clamp01((levelDbfs+52)/38);
        double time=clamp01(1-maxInterpolationGapMs/110.0);
        double motion=clamp01(1-angularSpeed/70.0);
        double quality=.30*c+.22*uniqueness+.16*peak+
            .10*level+.13*time+.09*motion;
        if(hasAmbiguousTone)quality*=.5;
        boolean accepted=quality>=.58 && !hasAmbiguousTone;
        return new Verdict(quality,accepted,
            accepted?"ACEITO":hasAmbiguousTone?"TONAL_AMBIGUO":"QUALIDADE_INSUFICIENTE");
    }
    public static double clamp01(double x){
        return Math.max(0,Math.min(1,x));
    }
}
