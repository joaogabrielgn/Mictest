package com.acousticdirection.lab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Local repeatability check ONLY for events recorded at nearly the same pose.
 * Rolling median/MAD is diagnostic, not a calibrated angular confidence.
 */
public final class DelayStability {
    private static final long HORIZON_NS = 2_500_000_000L;
    private static final double MAX_ORIENTATION_RADIANS = Math.toRadians(8);
    private final ArrayDeque<Sample> recent=new ArrayDeque<>();
    private static final class Sample {
        final long time;final double delay;final double[] q;
        Sample(long t,double d,double[] q){time=t;delay=d;this.q=q.clone();}
    }
    public static final class Stats {
        public final int count;
        public final double medianUs, madUs;
        public final boolean outlier;
        Stats(int n,double median,double mad,boolean outlier){
            count=n;medianUs=median;madUs=mad;this.outlier=outlier;
        }
    }
    public Stats observe(long timeNs, PoseHistory.Pose pose, double delayUs, boolean valid){
        if(timeNs<=0 || pose==null || !pose.valid || !Double.isFinite(delayUs))
            return new Stats(0,Double.NaN,Double.NaN,false);
        while(!recent.isEmpty() && timeNs-recent.peekFirst().time>HORIZON_NS)
            recent.removeFirst();
        ArrayList<Double> neighbor=new ArrayList<>();
        for(Sample s:recent)
            if(Math.abs(s.time-timeNs)<=HORIZON_NS && orientationClose(s.q,pose.q))
                neighbor.add(s.delay);
        // Compare to neighbors BEFORE admitting the new sample.
        double med=median(neighbor);
        ArrayList<Double> offsets=new ArrayList<>();
        for(double v:neighbor)offsets.add(Math.abs(v-med));
        double mad=median(offsets);
        boolean outlier=neighbor.size()>=3 &&
            Math.abs(delayUs-med)>Math.max(90.0,4.0*1.4826*mad);
        if(valid&&!outlier){
            recent.addLast(new Sample(timeNs,delayUs,pose.q));
            while(recent.size()>64)recent.removeFirst();
        }
        return new Stats(neighbor.size(),med,mad,outlier);
    }
    static boolean orientationClose(double[] a,double[] b){
        double dot=0;for(int i=0;i<4;i++)dot+=a[i]*b[i];
        dot=Math.min(1,Math.abs(dot));
        return 2*Math.acos(dot)<=MAX_ORIENTATION_RADIANS;
    }
    static double median(ArrayList<Double> values){
        if(values.isEmpty())return Double.NaN;
        Collections.sort(values);
        int n=values.size();
        return n%2==1 ? values.get(n/2) : (values.get(n/2-1)+values.get(n/2))*.5;
    }
}
