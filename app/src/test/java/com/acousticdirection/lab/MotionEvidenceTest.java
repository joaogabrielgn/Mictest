package com.acousticdirection.lab;

import org.junit.Test;
import static org.junit.Assert.*;

public class MotionEvidenceTest {
    private double[] q(double degrees){
        double h=Math.toRadians(degrees)/2.0;
        return new double[]{Math.cos(h),0,0,Math.sin(h)};
    }
    @Test public void stationaryPoseIsEligibleOnlyAsStationaryEvidence(){
        MotionEvidence m=new MotionEvidence();
        long base=4_000_000_000L;
        MotionEvidence.Result r=null;
        for(int i=0;i<50;i++)r=m.observe(base+i*20_000_000L,q(.2),30,true);
        assertNotNull(r);
        assertEquals("PARADO",r.status);
        assertTrue(r.stationary);
        assertFalse(r.moving);
    }
    @Test public void rotationWithPlausibleDelayChangeIsFlaggedMovingNotStable(){
        MotionEvidence m=new MotionEvidence();
        long base=2_000_000_000L;
        MotionEvidence.Result r=null;
        for(int i=0;i<=30;i++){
            double deg=i*2.0; // ~100 degrees/sec
            double delay=220*Math.sin(Math.toRadians(deg));
            r=m.observe(base+i*20_000_000L,q(deg),delay,true);
        }
        assertNotNull(r);
        assertTrue(r.moving);
        assertFalse(r.stationary);
        assertTrue(r.plausible);
        assertEquals("ROTACAO_PLAUSIVEL",r.status);
    }
    @Test public void largeInstantDelayCannotBeExplainedBySmallRotation(){
        MotionEvidence m=new MotionEvidence();
        long base=3_000_000_000L;
        m.observe(base,q(0),20,true);
        m.observe(base+20_000_000L,q(10),22,true);
        MotionEvidence.Result r=m.observe(base+40_000_000L,q(20),900,true);
        assertTrue(r.moving);
        assertFalse(r.plausible);
        assertEquals("ROTACAO_INCOMPATIVEL",r.status);
    }
    @Test public void missingPoseCannotValidateDirection(){
        MotionEvidence m=new MotionEvidence();
        MotionEvidence.Result r=m.observe(-1,null,300,true);
        assertFalse(r.poseValid);
        assertFalse(r.stationary);
    }
    @Test public void quaternionSignDoubleCoverMakesNoArtificialRotation(){
        assertEquals(0,MotionEvidence.angleDegrees(q(33),new double[]{
            -q(33)[0],0,0,-q(33)[3]}),1e-6);
    }
}
