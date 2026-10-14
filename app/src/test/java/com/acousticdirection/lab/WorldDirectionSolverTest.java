package com.acousticdirection.lab;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Random;

public class WorldDirectionSolverTest {
    private static double[] norm(double[] a){
        double r=0;for(double v:a)r+=v*v;
        r=Math.sqrt(r);
        return new double[]{a[0]/r,a[1]/r,a[2]/r};
    }
    private static double[] qAxis(double degrees,double x,double y,double z){
        double a=Math.toRadians(degrees)/2;
        double s=Math.sin(a);
        return new double[]{Math.cos(a),s*x,s*y,s*z};
    }
    private static double[] mul(double[] a,double[] b){
        return new double[]{
            a[0]*b[0]-a[1]*b[1]-a[2]*b[2]-a[3]*b[3],
            a[0]*b[1]+a[1]*b[0]+a[2]*b[3]-a[3]*b[2],
            a[0]*b[2]-a[1]*b[3]+a[2]*b[0]+a[3]*b[1],
            a[0]*b[3]+a[1]*b[2]-a[2]*b[1]+a[3]*b[0]};
    }
    private static double scoreToDelay(double p){
        return p>0?EmpiricalAxis.CENTER_US+
            p*(EmpiricalAxis.SUPERIOR_US-EmpiricalAxis.CENTER_US):
            EmpiricalAxis.CENTER_US+
            p*(EmpiricalAxis.CENTER_US-EmpiricalAxis.INFERIOR_US);
    }
    private static double[] q(double yaw,double pitch,double roll){
        return mul(mul(qAxis(yaw,0,0,1),qAxis(pitch,1,0,0)),qAxis(roll,0,1,0));
    }
    @Test public void full3DRotationsRecoverSyntheticWorldDirection(){
        WorldDirectionSolver solver=new WorldDirectionSolver();
        double[] source=norm(new double[]{.50,.43,-.75});
        long t=1_000_000_000L;
        WorldDirectionSolver.Result r=null;
        for(int i=0;i<140;i++){
            double yaw=80*Math.sin(i*.077);
            double pitch=45*Math.sin(i*.109);
            double roll=32*Math.cos(i*.063);
            double[] quat=q(yaw,pitch,roll);
            double p=WorldDirectionSolver.rotate(quat,
                WorldDirectionSolver.microphoneAxisDevice())[0]*source[0]+
                WorldDirectionSolver.rotate(quat,
                WorldDirectionSolver.microphoneAxisDevice())[1]*source[1]+
                WorldDirectionSolver.rotate(quat,
                WorldDirectionSolver.microphoneAxisDevice())[2]*source[2];
            r=solver.add(t,quat,scoreToDelay(p),true);
            t+=170_000_000L;
        }
        assertNotNull(r);
        assertTrue("Full rotation coverage should yield 3D experimental estimate: "+r.status,
            r.available);
        double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,
            source[0]*r.world[0]+source[1]*r.world[1]+source[2]*r.world[2]))));
        assertTrue("Synthetic angular error expected < 2 degrees; got "+angle,angle<2);
        assertTrue(r.rmsProjection<.035);
        assertTrue(r.rankRatio>.022);
    }
    @Test public void purelyPlanarRotationMustNotPretendToBe3D(){
        WorldDirectionSolver solver=new WorldDirectionSolver();
        double[] source=norm(new double[]{.2,.75,-.6});
        long t=3_000_000_000L;
        WorldDirectionSolver.Result r=null;
        for(int i=0;i<140;i++){
            double[] quat=q(80*Math.sin(i*.11),0,0);
            double[] mic=WorldDirectionSolver.rotate(quat,WorldDirectionSolver.microphoneAxisDevice());
            double proj=mic[0]*source[0]+mic[1]*source[1]+mic[2]*source[2];
            r=solver.add(t,quat,scoreToDelay(proj),true);
            t+=170_000_000L;
        }
        assertNotNull(r);
        assertFalse("Only turning in one plane must not yield 3D",r.available);
        assertEquals("EIXOS_NAO_COBREM_3D",r.status);
    }
    @Test public void noRotationMeansNo3D(){
        WorldDirectionSolver solver=new WorldDirectionSolver();
        long t=1_000_000_000L;
        WorldDirectionSolver.Result r=null;
        double[] staticQ=q(0,0,0);
        for(int i=0;i<60;i++){
            r=solver.add(t,staticQ,-100,true);
            t+=170_000_000L;
        }
        assertFalse(r.available);
        assertEquals("VARREDURA_ANGULAR_INSUFICIENTE",r.status);
    }
    @Test public void resetClearsPriorSourceEvidence(){
        WorldDirectionSolver solver=new WorldDirectionSolver();
        solver.add(1_000_000_000L,q(0,0,0),-50,true);
        assertEquals(1,solver.points());
        solver.reset();
        assertEquals(0,solver.points());
        assertFalse(solver.solve(q(0,0,0)).available);
    }
    @Test public void relativeCameraProjectionMatchesQuaternionIdentity(){
        WorldDirectionSolver s=new WorldDirectionSolver();
        double[] point={0,0,-1};
        double[] same=WorldDirectionSolver.rotate(new double[]{1,0,0,0},point);
        assertArrayEquals(point,same,1e-9);
    }
    @Test public void grossPilotRangeOutlierCannotEnter3DModel(){
        WorldDirectionSolver solver=new WorldDirectionSolver();
        solver.add(1_000_000_000L,q(0,0,0),2100,true);
        assertEquals(0,solver.points());
    }
}
