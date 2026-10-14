package com.acousticdirection.lab;

import java.util.ArrayDeque;

/**
 * Copies ARCore world pose geometry into immutable BOOTTIME-tagged samples.
 * ARCore frame exposure and audio capture are NOT hardware synchronized here;
 * BOOTTIME is sampled after Session.update(), so timing error is explicitly
 * reported and rapidly moving frames must be rejected for calibration.
 */
public final class ArFrameBridge {
    public static final class Sample {
        public final long boottimeNs,cameraFrameNs;
        public final double[] axisWorld,sourceWorld,positionWorld,sensorQuaternion;
        public final boolean anchorMode;
        public final double translationFromMarkMeters;
        public final double angularSpeedDegSec;
        Sample(long t,long frame,double[] axis,double[] source,double[] position,
            double[] sensorQuaternion,boolean anchored,double translated,double speed){
            boottimeNs=t;cameraFrameNs=frame;
            axisWorld=axis.clone();sourceWorld=source.clone();
            positionWorld=position.clone();
            this.sensorQuaternion=sensorQuaternion.clone();anchorMode=anchored;
            translationFromMarkMeters=translated;angularSpeedDegSec=speed;
        }
        public double expectedProjection(){
            return axisWorld[0]*sourceWorld[0]+
                axisWorld[1]*sourceWorld[1]+axisWorld[2]*sourceWorld[2];
        }
    }
    private final ArrayDeque<Sample> history=new ArrayDeque<>();
    private Sample previous=null;
    public synchronized void clear(){history.clear();previous=null;}
    public synchronized void append(Sample s){
        if(previous!=null&&s.boottimeNs<=previous.boottimeNs)return;
        history.addLast(s);previous=s;
        while(history.size()>170)history.removeFirst();
        while(!history.isEmpty() && s.boottimeNs-history.peekFirst().boottimeNs>6_000_000_000L)
            history.removeFirst();
    }
    public synchronized Sample closest(long t){
        Sample best=null;long diff=Long.MAX_VALUE;
        for(Sample s:history){
            long d=Math.abs(s.boottimeNs-t);
            if(d<diff){diff=d;best=s;}
        }
        return diff<=125_000_000L?best:null;
    }
    public static Sample create(long boottimeNs,long frameNs,double[] axis,double[] source,
        double[] position,double[] sensorQuat,boolean anchor,double translation,double angularSpeed){
        return new Sample(boottimeNs,frameNs,axis,source,position,sensorQuat,anchor,
            translation,angularSpeed);
    }
    public static double[] unit(double[] v){
        double n=0;for(double x:v)n+=x*x;
        n=Math.sqrt(n);
        if(n<1e-12)return new double[]{Double.NaN,Double.NaN,Double.NaN};
        return new double[]{v[0]/n,v[1]/n,v[2]/n};
    }
    public static double distance(double[] a,double[] b){
        double sum=0;for(int i=0;i<3;i++)sum+=(a[i]-b[i])*(a[i]-b[i]);
        return Math.sqrt(sum);
    }
}
