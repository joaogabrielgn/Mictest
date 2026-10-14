package com.acousticdirection.lab;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Frame.getTimestamp() is the exposure timestamp, unlike elapsedRealtimeNanos
 * sampled after Session.update(). ARCore doesn't contractually guarantee the
 * timebase, so a runtime plausibility check is mandatory (FrameClockGate).
 *
 * A causal interpolation needs a frame BEFORE and AFTER the captured audio
 * window. It must not silently substitute the newest, delayed camera frame.
 */
public final class ArFrameBridge {
    public static final class Sample {
        public final long boottimeNs,cameraFrameNs,processedAtNs;
        public final double[] axisWorld,sourceWorld,positionWorld,sensorQuaternion;
        public final boolean anchorMode;
        public final double translationFromMarkMeters,angularSpeedDegSec;
        Sample(long capture,long cameraTimestamp,long processedAt,
               double[] axis,double[] source,double[] position,double[] quaternion,
               boolean anchored,double translation,double speed){
            boottimeNs=capture;cameraFrameNs=cameraTimestamp;processedAtNs=processedAt;
            axisWorld=axis.clone();sourceWorld=source.clone();
            positionWorld=position.clone();sensorQuaternion=quaternion.clone();
            anchorMode=anchored;translationFromMarkMeters=translation;
            angularSpeedDegSec=speed;
        }
        public double expectedProjection(){
            return axisWorld[0]*sourceWorld[0]+axisWorld[1]*sourceWorld[1]+
                axisWorld[2]*sourceWorld[2];
        }
        public double frameAgeMs(){return (processedAtNs-cameraFrameNs)/1e6;}
    }
    public static final class Match {
        public final Sample pose;
        public final String status;
        public final double maxBracketMs,interpolationFraction;
        Match(Sample s,String status,double bracket,double frac){
            pose=s;this.status=status;maxBracketMs=bracket;interpolationFraction=frac;
        }
        public boolean valid(){return pose!=null;}
    }
    private final ArrayDeque<Sample> history=new ArrayDeque<>();
    public synchronized void clear(){history.clear();}
    public synchronized void append(Sample s){
        if(s.boottimeNs<=0)return;
        if(!history.isEmpty()&&s.boottimeNs<=history.getLast().boottimeNs)return;
        history.addLast(s);
        while(history.size()>270)history.removeFirst();
        while(!history.isEmpty()&&s.boottimeNs-history.getFirst().boottimeNs>6_000_000_000L)
            history.removeFirst();
    }
    public synchronized long newestExposureNs(){
        return history.isEmpty()?-1:history.getLast().boottimeNs;
    }
    public synchronized Sample closest(long timeNs){
        Sample best=null;long delta=Long.MAX_VALUE;
        for(Sample s:history){
            long d=Math.abs(timeNs-s.boottimeNs);
            if(d<delta){delta=d;best=s;}
        }
        return delta<=125_000_000L?best:null;
    }
    /** Requires bracketing exposures <= 110ms apart and no extrapolation. */
    public synchronized Match interpolate(long requested){
        if(requested<=0)return new Match(null,"SEM_TIMESTAMP_AUDIO",Double.NaN,Double.NaN);
        Sample before=null;
        for(Sample after:history){
            if(after.boottimeNs==requested)return new Match(after,"EXATA",0,0);
            if(after.boottimeNs>requested){
                if(before==null)return new Match(null,"SEM_FRAME_ANTERIOR",Double.NaN,Double.NaN);
                long gap=after.boottimeNs-before.boottimeNs;
                if(gap<=0||gap>110_000_000L)
                    return new Match(null,"INTERVALO_AR_EXCESSIVO",gap/1e6,Double.NaN);
                if(requested-before.boottimeNs>70_000_000L||
                   after.boottimeNs-requested>70_000_000L)
                    return new Match(null,"DISTANCIA_TEMPORAL_EXCESSIVA",
                        gap/1e6,Double.NaN);
                if(before.anchorMode!=after.anchorMode)
                    return new Match(null,"REFERENCIA_MUDOU",gap/1e6,Double.NaN);
                double fraction=(requested-before.boottimeNs)/(double)gap;
                return new Match(lerp(before,after,requested,fraction),"INTERPOLADA",
                    Math.max(requested-before.boottimeNs,
                        after.boottimeNs-requested)/1e6,fraction);
            }
            before=after;
        }
        return new Match(null,"AGUARDANDO_FRAME_POSTERIOR",Double.NaN,Double.NaN);
    }
    private static Sample lerp(Sample a,Sample b,long t,double w){
        double[] q=new double[4],axis=new double[3],source=new double[3],position=new double[3];
        double dot=0;for(int i=0;i<4;i++)dot+=a.sensorQuaternion[i]*b.sensorQuaternion[i];
        double sign=dot<0?-1:1, norm=0;
        for(int i=0;i<4;i++){
            q[i]=(1-w)*a.sensorQuaternion[i]+w*b.sensorQuaternion[i]*sign;
            norm+=q[i]*q[i];
        }
        norm=Math.sqrt(norm);
        for(int i=0;i<4;i++)q[i]/=Math.max(1e-10,norm);
        for(int i=0;i<3;i++){
            axis[i]=(1-w)*a.axisWorld[i]+w*b.axisWorld[i];
            source[i]=(1-w)*a.sourceWorld[i]+w*b.sourceWorld[i];
            position[i]=(1-w)*a.positionWorld[i]+w*b.positionWorld[i];
        }
        source=unit(source);axis=unit(axis);
        return new Sample(t,
            (long)((1-w)*a.cameraFrameNs+w*b.cameraFrameNs),
            (long)((1-w)*a.processedAtNs+w*b.processedAtNs),axis,source,
            position,q,a.anchorMode,
            (1-w)*a.translationFromMarkMeters+w*b.translationFromMarkMeters,
            (1-w)*a.angularSpeedDegSec+w*b.angularSpeedDegSec);
    }
    public static Sample create(long captureNs,long rawFrameNs,double[] axis,
        double[] source,double[] position,double[] quaternion,
        boolean anchor,double translation,double speed){
        return new Sample(captureNs,rawFrameNs,captureNs,axis,source,position,quaternion,
            anchor,translation,speed);
    }
    public static Sample createWithTiming(long frameTimestampNs,long processedAtNs,
        double[] axis,double[] source,double[] position,double[] quaternion,
        boolean anchor,double translation,double speed){
        return new Sample(frameTimestampNs,frameTimestampNs,processedAtNs,axis,source,
            position,quaternion,anchor,translation,speed);
    }
    public static double[] unit(double[] v){
        double s=0;for(double x:v)s+=x*x;s=Math.sqrt(s);
        if(s<1e-12)return new double[]{Double.NaN,Double.NaN,Double.NaN};
        return new double[]{v[0]/s,v[1]/s,v[2]/s};
    }
    public static double distance(double[] a,double[] b){
        double n=0;for(int i=0;i<3;i++)n+=(a[i]-b[i])*(a[i]-b[i]);
        return Math.sqrt(n);
    }
}
