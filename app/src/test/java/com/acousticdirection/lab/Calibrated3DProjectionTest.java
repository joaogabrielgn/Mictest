package com.acousticdirection.lab;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Random;

public class Calibrated3DProjectionTest {
    private double[] quaternion(double angle){
        double half=Math.toRadians(angle)/2;
        return new double[]{Math.cos(half),0,0,Math.sin(half)};
    }
    @Test public void explicitProjectionFeeds3DSolver(){
        WorldDirectionSolver solver=new WorldDirectionSolver();
        double[] target={0.4,0.9,0.0};
        double norm=Math.hypot(target[0],target[1]);
        target[0]/=norm;target[1]/=norm;
        long time=1_000_000_000L;
        for(int i=0;i<120;i++){
            double[] q=quaternion(i*2.0);
            double[] axis=WorldDirectionSolver.rotate(q,WorldDirectionSolver.microphoneAxisDevice());
            double projection=axis[0]*target[0]+axis[1]*target[1];
            solver.addProjection(time,q,projection,true);
            time+=180_000_000L;
        }
        // Rotation around one axis only cannot independently estimate
        // the missing 3D component, even when target was in the plane.
        assertFalse(solver.solve(quaternion(0)).available);
    }
}
