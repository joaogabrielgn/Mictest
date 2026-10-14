package com.acousticdirection.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class DelayStabilityTest {
    private PoseHistory.Pose poseAt(long t,double degrees) {
        PoseHistory h=new PoseHistory();
        double a=Math.toRadians(degrees)*0.5;
        h.add(t,Math.cos(a),0,0,Math.sin(a));
        return h.interpolate(t);
    }
    @Test public void rejectsOutlierWithStablePose(){
        DelayStability st=new DelayStability();
        long t=1_000_000_000L;
        for(double delay : new double[]{21,25,24}){
            DelayStability.Stats info=st.observe(t,poseAt(t,0),delay,true);
            assertFalse(info.outlier);
            t+=180_000_000L;
        }
        DelayStability.Stats out=st.observe(t,poseAt(t,0),360,true);
        assertTrue(out.outlier);
        assertEquals(3,out.count);
    }
    @Test public void movingPhoneDoesNotCompareDifferentOrientations(){
        DelayStability st=new DelayStability();
        long t=1_000_000_000L;
        st.observe(t,poseAt(t,0),20,true);
        t+=100_000_000L;
        DelayStability.Stats moved=st.observe(t,poseAt(t,90),350,true);
        assertEquals(0,moved.count);
        assertFalse(moved.outlier);
    }
}
