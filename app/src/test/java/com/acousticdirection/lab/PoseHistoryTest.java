package com.acousticdirection.lab;

import static org.junit.Assert.*;
import org.junit.Test;

public class PoseHistoryTest {
    @Test public void interpolatesHalfRotation(){
        PoseHistory poses=new PoseHistory();
        long zero=1_000_000_000L;
        poses.add(zero,1,0,0,0);
        poses.add(zero+20_000_000L,0,0,0,1);
        PoseHistory.Pose middle=poses.interpolate(zero+10_000_000L);
        assertTrue(middle.valid);
        assertEquals("INTERPOLADA",middle.quality);
        assertEquals(Math.sqrt(.5), Math.abs(middle.q[0]),1e-5);
        assertEquals(Math.sqrt(.5), Math.abs(middle.q[3]),1e-5);
        assertEquals(1,Math.sqrt(
            middle.q[0]*middle.q[0]+middle.q[1]*middle.q[1]+
            middle.q[2]*middle.q[2]+middle.q[3]*middle.q[3]),1e-5);
    }
    @Test public void refusesExtrapolationAndMissingBracket(){
        PoseHistory poses=new PoseHistory();
        poses.add(1_000_000_000L,1,0,0,0);
        poses.add(1_010_000_000L,1,0,0,0);
        assertFalse(poses.interpolate(990_000_000L).valid);
        assertFalse(poses.interpolate(1_030_000_000L).valid);
        assertTrue(poses.interpolate(1_005_000_000L).valid);
    }
    @Test public void refusesWideGaps(){
        PoseHistory poses=new PoseHistory();
        poses.add(1_000_000_000L,1,0,0,0);
        poses.add(1_300_000_000L,0,0,0,1);
        assertFalse(poses.interpolate(1_150_000_000L).valid);
    }
}
