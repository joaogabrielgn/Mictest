package com.acousticdirection.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArInterpolationTest {
    private ArFrameBridge.Sample s(long ns,double[] axis){
        return ArFrameBridge.createWithTiming(ns,ns+190_000_000L,
            axis,new double[]{0,1,0},new double[]{0,0,0},
            new double[]{1,0,0,0},true,0,2);
    }
    @Test public void interpolationUsesExposureNotRenderCallbackClock(){
        ArFrameBridge bridge=new ArFrameBridge();
        bridge.append(s(1_000_000_000L,new double[]{1,0,0}));
        bridge.append(s(1_040_000_000L,new double[]{0,1,0}));
        ArFrameBridge.Match match=bridge.interpolate(1_020_000_000L);
        assertTrue(match.status,match.valid());
        assertEquals("INTERPOLADA",match.status);
        assertEquals(20,match.maxBracketMs,1e-8);
        assertEquals(Math.sqrt(.5),match.pose.axisWorld[0],1e-6);
        assertEquals(190,match.pose.frameAgeMs(),1e-6);
    }
    @Test public void noExtrapolationOrLongGap(){
        ArFrameBridge b=new ArFrameBridge();
        b.append(s(1_000_000_000L,new double[]{1,0,0}));
        assertFalse(b.interpolate(1_030_000_000L).valid());
        b.append(s(1_170_000_000L,new double[]{0,1,0}));
        assertEquals("INTERVALO_AR_EXCESSIVO",b.interpolate(1_100_000_000L).status);
    }
}
