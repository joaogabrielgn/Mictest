package com.acousticdirection.lab;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Random;

public class SpatialDspTest {
    private static final int N = 2048;
    private double[] randomNoise(long seed) {
        Random random = new Random(seed);
        double[] a = new double[N];
        for(int i=0; i<N; i++) a[i] = 0.10*random.nextGaussian();
        return a;
    }
    private double rms(double[] a) {
        double sum=0;
        for(double value:a)sum+=value*value;
        return Math.sqrt(sum/a.length);
    }
    private double[] delayed(double[] input, int samples) {
        double[] b=new double[N];
        for(int i=0;i<N;i++){
            int j=i-samples;
            b[i] = (j>=0 && j<N) ? input[j] : 0;
        }
        return b;
    }
    @Test public void detectsPositiveDelay() {
        double[] a=randomNoise(31), b=delayed(a,8);
        SpatialDsp.Estimate e=SpatialDsp.estimatePair(a,b,rms(a),rms(b),0,1,48000,26);
        assertEquals(8.0,e.delaySamples,0.6);
        assertTrue(e.peak>2.0);
    }
    @Test public void detectsNegativeDelay() {
        double[] a=randomNoise(32), b=delayed(a,-11);
        SpatialDsp.Estimate e=SpatialDsp.estimatePair(a,b,rms(a),rms(b),0,1,48000,26);
        assertEquals(-11.0,e.delaySamples,0.6);
    }
    @Test public void silenceIsRejected() {
        double[] a = new double[N];
        SpatialDsp.Estimate e=SpatialDsp.estimatePair(a,a,0,0,0,1,48000,26);
        assertFalse(e.valid);
        assertEquals("SINAL_FRACO",e.reason);
    }
    @Test public void duplicateChannelsAreRejected() {
        double[] a=randomNoise(33);
        SpatialDsp.Estimate e=SpatialDsp.estimatePair(a,a,rms(a),rms(a),0,1,48000,26);
        assertFalse(e.valid);
        assertEquals("CANAIS_SIMILARES",e.reason);
    }
    @Test public void shortSinglePeakIsFlaggedAsClipping() {
        double[] a=randomNoise(41), b=delayed(a,5);
        SpatialDsp.Estimate result=SpatialDsp.estimatePair(a,b,rms(a),rms(b),
            1.0/N,1,48000,26);
        assertFalse(result.valid);
        assertEquals("CLIPPING",result.reason);
    }
    @Test public void processedChannelDelayBeyondIdealAirLimitRemainsDiagnostic() {
        double[] a=randomNoise(42),b=delayed(a,24);
        SpatialDsp.Estimate result=SpatialDsp.estimatePair(a,b,rms(a),rms(b),
            0,1,48000,42);
        assertEquals(24.0,result.delaySamples,0.6);
        assertTrue("Physical warning must be independent from correlation quality",
            result.beyondIdealAirLimit);
        assertTrue(result.idealAirExcessUs>0);
        assertNotEquals("FORA_LIMITE_FISICO",result.reason);
    }
    @Test public void oppositeProcessedDelaysHaveCorrectSigns() {
        double[] a=randomNoise(51), pos=delayed(a,23), neg=delayed(a,-21);
        SpatialDsp.Estimate positive=SpatialDsp.estimatePair(
            a,pos,rms(a),rms(pos),0,1,48000,42);
        SpatialDsp.Estimate negative=SpatialDsp.estimatePair(
            a,neg,rms(a),rms(neg),0,1,48000,42);
        assertEquals(23.0,positive.delaySamples,0.6);
        assertEquals(-21.0,negative.delaySamples,0.6);
        assertTrue(positive.beyondIdealAirLimit);
        assertTrue(negative.beyondIdealAirLimit);
    }
    @Test public void clippingIsRejected() {
        double[] a=randomNoise(34),b=delayed(a,4);
        SpatialDsp.Estimate e=SpatialDsp.estimatePair(a,b,rms(a),rms(b),.4,1,48000,26);
        assertFalse(e.valid);
        assertEquals("CLIPPING",e.reason);
    }
}
