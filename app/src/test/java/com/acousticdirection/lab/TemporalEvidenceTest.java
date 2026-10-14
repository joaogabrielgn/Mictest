package com.acousticdirection.lab;

import org.junit.Test;
import static org.junit.Assert.*;

public class TemporalEvidenceTest {
    @Test public void stationarySingleBandNeedsTimeAndMultipleWindows(){
        TemporalEvidence e=new TemporalEvidence(48000);
        TemporalEvidence.Result initial=e.observe(1024,218,true,false);
        assertFalse(initial.stable);
        TemporalEvidence.Result last=initial;
        for(int i=1;i<=75;i++){
            double d=218+(i%5-2)*2.0;
            last=e.observe(1024L+(long)i*1024,d,true,false);
        }
        assertTrue(last.stable);
        assertEquals("TEMPORAL_ESTAVEL",last.status);
        assertTrue(last.spanSeconds>=.65);
        assertTrue(last.madUs<=25);
    }
    @Test public void changingDelayOrTonalityCannotBeReportedAsStable(){
        TemporalEvidence e=new TemporalEvidence(48000);
        TemporalEvidence.Result r=null;
        for(int i=0;i<75;i++)
            r=e.observe(4096L+i*1024,i*12.0,true,false);
        assertFalse(r.stable);
        TemporalEvidence tonal=new TemporalEvidence(48000);
        for(int i=0;i<75;i++)
            r=tonal.observe(4096L+i*1024,150,true,true);
        assertFalse(r.stable);
        assertEquals("TONAL_SEM_VALIDACAO",r.status);
    }
    @Test public void staleSampleDoesNotFakeEvidence(){
        TemporalEvidence e=new TemporalEvidence(48000);
        for(int i=0;i<75;i++)e.observe(4096L+i*1024,250,true,false);
        TemporalEvidence.Result r=e.observe(800000L,250,true,false);
        assertFalse(r.stable);
        assertEquals(1,r.matched);
    }
    @Test public void silenceAndNaNAreNotMeasurements(){
        TemporalEvidence e=new TemporalEvidence(48000);
        assertEquals("SEM_MEDICAO",e.observe(10000,Double.NaN,false,false).status);
        assertFalse(e.observe(50000,300,false,false).stable);
    }
}
