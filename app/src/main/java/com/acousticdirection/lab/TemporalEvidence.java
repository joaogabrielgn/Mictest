package com.acousticdirection.lab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Temporal evidence for a *single measured delay* in a stationary/slowly varying
 * sound field. This is not an angular confidence score, source separation or
 * probability. A sequence of highly correlated overlapping windows is NOT a
 * set of independent observations; require elapsed time and a robust MAD.
 */
public final class TemporalEvidence {
    private static final long MAX_AGE_FRAMES_AT_48K = 4 * 48000L;
    private final int sampleRate;
    private final ArrayDeque<Sample> history = new ArrayDeque<>();
    private static final class Sample {
        final long frame; final double delay;
        Sample(long f,double d){frame=f;delay=d;}
    }
    public static final class Result {
        public final int matched;
        public final double medianUs,madUs,spanSeconds;
        public final boolean stable;
        public final String status;
        Result(int n,double med,double mad,double span,boolean stable,String status){
            matched=n;medianUs=med;madUs=mad;spanSeconds=span;
            this.stable=stable;this.status=status;
        }
    }
    public TemporalEvidence(int sampleRate) {
        if(sampleRate<8000)throw new IllegalArgumentException("sampleRate");
        this.sampleRate=sampleRate;
    }
    public void reset(){history.clear();}
    public Result observe(long frame,double delayUs,boolean usable,boolean tonal){
        while(!history.isEmpty() && frame-history.peekFirst().frame > 4L*sampleRate)
            history.removeFirst();
        if(!usable || !Double.isFinite(delayUs)){
            // Invalid windows never enter the history, but do not clear it just
            // because one FFT result is ambiguous.
            return new Result(0,Double.NaN,Double.NaN,0,false,"SEM_MEDICAO");
        }
        history.addLast(new Sample(frame,delayUs));
        if(history.size()>200)history.removeFirst();
        // Find a temporal cluster centered at current delay, with max 65 us.
        // A moving source can cause systematic evolution; then don't claim stability.
        ArrayList<Double> neighboring=new ArrayList<>();
        long first=frame,last=frame;
        for(Sample s:history){
            if(Math.abs(delayUs-s.delay)<=65){
                neighboring.add(s.delay);
                first=Math.min(first,s.frame);
                last=Math.max(last,s.frame);
            }
        }
        double median=median(neighboring);
        ArrayList<Double> deviations=new ArrayList<>();
        for(double v:neighboring)deviations.add(Math.abs(v-median));
        double mad=median(deviations);
        double span=(last-first)/(double)sampleRate;
        // 12+ overlapping frames / at least 0.65 s / MAD <= 25 us,
        // additionally checking recent medians remain near current observation.
        boolean stable=neighboring.size()>=12 && span>=0.65 &&
            mad<=25 && Math.abs(delayUs-median)<=35 && !tonal;
        return new Result(neighboring.size(),median,mad,span,
            stable,stable?"TEMPORAL_ESTAVEL":tonal?"TONAL_SEM_VALIDACAO":"SEM_ESTABILIDADE");
    }
    private static double median(ArrayList<Double> values) {
        if(values.isEmpty())return Double.NaN;
        Collections.sort(values);
        int n=values.size();
        return (n&1)==1?values.get(n/2):.5*(values.get(n/2-1)+values.get(n/2));
    }
}
