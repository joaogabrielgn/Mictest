package com.acousticdirection.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameClockGateTest {
    @Test public void acceptsOnlyPlausibleExposureTimebase(){
        FrameClockGate g=new FrameClockGate();
        long t=1_000_000_000L;
        for(int i=0;i<15;i++){
            assertEquals(i>=4,g.observe(t+i*33_000_000L,
                t+i*33_000_000L+190_000_000L));
        }
        assertTrue(g.compatible());assertEquals(190,g.medianFrameAgeMs(),1);
        assertTrue(g.observe(t+14*33_000_000L,t+14*33_000_000L+200_000_000L));
    }
    @Test public void rejectsTimestampInAnotherEpochOrReverseTime(){
        FrameClockGate g=new FrameClockGate();
        long t=1_000_000_000L;
        for(int i=0;i<8;i++)g.observe(t+i*33_000_000L,t+i*33_000_000L+190_000_000L);
        assertFalse(g.observe(10_000L,3_000_000_000L));
        assertEquals("RELOGIOS_INCOMPATIVEIS",g.status());
    }
}
