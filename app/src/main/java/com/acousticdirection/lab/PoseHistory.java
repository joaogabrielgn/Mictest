package com.acousticdirection.lab;

import java.util.ArrayDeque;

/** Quaternion in Android SensorManager getQuaternionFromVector order [w,x,y,z]. */
public final class PoseHistory {
    private static final int MAX=768;
    private final ArrayDeque<Pose> poses=new ArrayDeque<>();

    public static final class Pose {
        public final long timeNs;
        public final double[] q;
        public final boolean valid;
        public final String quality;
        public final double yaw,pitch,roll;
        Pose(long t,double[] q, boolean v, String quality){
            timeNs=t;this.q=q;valid=v;this.quality=quality;
            double w=q[0],x=q[1],y=q[2],z=q[3];
            double r01=2*(x*y-z*w),r11=1-2*(x*x+z*z),
                   r21=2*(y*z+x*w),r20=2*(x*z-y*w),r22=1-2*(x*x+y*y);
            yaw=Math.toDegrees(Math.atan2(r01,r11));
            pitch=Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,-r21))));
            roll=Math.toDegrees(Math.atan2(-r20,r22));
        }
        public String csvColumns(){
            return String.format(java.util.Locale.US,
                "%s,%d,%.8f,%.8f,%.8f,%.8f,%.3f,%.3f,%.3f",
                quality,timeNs,q[0],q[1],q[2],q[3],yaw,pitch,roll);
        }
    }
    public synchronized void clear(){poses.clear();}
    public synchronized void add(long timeNs,double w,double x,double y,double z){
        if(!poses.isEmpty() && poses.getLast().timeNs>=timeNs)return;
        double[] q=normalise(new double[]{w,x,y,z});
        poses.addLast(new Pose(timeNs,q,true,"RAW"));
        if(poses.size()>MAX)poses.removeFirst();
    }
    public synchronized Pose interpolate(long timeNs){
        if(timeNs<=0 || poses.isEmpty())return invalid("SEM_IMU");
        Pose before=null;
        for(Pose after:poses){
            if(after.timeNs==timeNs)return new Pose(timeNs,after.q,true,"EXACT");
            if(after.timeNs>timeNs){
                if(before==null)return invalid("IMU_INICIO");
                long dt=after.timeNs-before.timeNs;
                if(dt<=0 || dt>150_000_000L)return invalid("IMU_INTERVALO");
                if(timeNs-before.timeNs>100_000_000L || after.timeNs-timeNs>100_000_000L)
                    return invalid("IMU_DISTANTE");
                double alpha=(timeNs-before.timeNs)/(double)dt;
                return new Pose(timeNs,slerp(before.q,after.q,alpha),true,"INTERPOLADA");
            }
            before=after;
        }
        return invalid("IMU_SEM_BRACKET"); // no extrapolation to look confident
    }
    private static Pose invalid(String message){
        return new Pose(-1,new double[]{1,0,0,0},false,message);
    }
    static double[] normalise(double[] q){
        double n=0;for(double d:q)n+=d*d;
        n=Math.sqrt(n);
        if(n<1e-12)return new double[]{1,0,0,0};
        for(int i=0;i<q.length;i++)q[i]/=n;
        return q;
    }
    static double[] slerp(double[] a,double[] b,double t){
        double dot=0;for(int i=0;i<4;i++)dot+=a[i]*b[i];
        double[] end=b.clone();
        if(dot<0){dot=-dot;for(int i=0;i<4;i++)end[i]=-end[i];}
        dot=Math.min(1,Math.max(-1,dot));
        double x,y;
        if(dot>.9995){x=1-t;y=t;}
        else{
            double theta=Math.acos(dot),den=Math.sin(theta);
            x=Math.sin((1-t)*theta)/den;y=Math.sin(t*theta)/den;
        }
        double[] result=new double[4];
        for(int i=0;i<4;i++)result[i]=x*a[i]+y*end[i];
        return normalise(result);
    }
}
