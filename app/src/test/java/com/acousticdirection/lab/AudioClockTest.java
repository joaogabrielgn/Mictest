package com.acousticdirection.lab;

import static org.junit.Assert.*;
import org.junit.Test;

public class AudioClockTest {
    @Test public void frameToBoottime() {
        AudioClock clock=new AudioClock(48000);
        long base=5_000_000_000L;
        assertTrue(clock.update(4800,base,5000,base+8_000_000L));
        AudioClock.Mapping map=clock.timeForFrame(5280);
        assertTrue(map.valid);
        assertEquals(base+10_000_000L,map.timeNs);
    }
    @Test public void rejectsDiscontinuousAnchor(){
        AudioClock clock=new AudioClock(48000);
        assertTrue(clock.update(4800,5_000_000_000L,5000,5_008_000_000L));
        assertFalse(clock.update(5280,5_100_000_000L,5400,5_103_000_000L));
        assertFalse(clock.timeForFrame(5400).valid);
    }
    @Test public void invalidWithoutTimestamp(){
        AudioClock clock=new AudioClock(48000);
        assertEquals("SEM_TIMESTAMP",clock.timeForFrame(100).status);
    }
}
