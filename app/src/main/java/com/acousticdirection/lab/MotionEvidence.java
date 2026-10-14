package com.acousticdirection.lab;

import java.util.ArrayDeque;

/**
 * Motion gate for a single 1D measured acoustic delay.
 *
 * Motion cannot recover 3D direction without a calibrated array model.
 * This verifies that successive delay changes are NOT obviously faster than
 * allowed by rotation of the ~14.26cm microphone baseline.
 * Smartphone HAL processing may still introduce arbitrary delay changes.
 */
public final class MotionEvidence {
    public static final class Result {
        public final String status;
        public final boolean poseValid, moving, plausible, stationary;
        public final double speedDegPerSec, relativeDegrees, maxGeometricChangeUs;
        Result(String status,boolean valid,boolean moving,boolean plausible,
               boolean stationary,double speed,double delta,double bound){
            this.status=status;poseValid=valid;this.moving=moving;
            this.plausible=plausible;this.stationary=stationary;
            speedDegPerSec=speed;relativeDegrees=delta;
            maxGeometricChangeUs=bound;
        }
    }
    private static final class State {
        final long timeNs;
        final double[] q;
        final double delay;
        State(long t,double[] q,double d){timeNs=t;this.q=q.clone();delay=d;}
    }
    private final ArrayDeque<State> history=new ArrayDeque<>();
    private State previous=null;
    private double speedEma=0;

    public Result observe(long timestampNs,double[] quat,double delayUs,boolean usable){
        if(timestampNs<=0||quat==null||quat.length!=4||!usable||
           !Double.isFinite(delayUs))
            return new Result("SEM_DADO_SINCRONIZADO",false,false,false,false,
                Double.NaN,Double.NaN,Double.NaN);
        if(previous!=null&&timestampNs<=previous.timeNs){
            reset();
            return new Result("TEMPO_NAO_MONOTONICO",false,false,false,false,
                Double.NaN,Double.NaN,Double.NaN);
        }
        double speed=0,angle=0;
        if(previous!=null){
            angle=angleDegrees(previous.q,quat);
            double dt=(timestampNs-previous.timeNs)/1.e9;
            if(dt<=0||dt>.35){reset();}
            else speed=angle/dt;
        }
        speedEma=.70*speedEma+.30*speed;
        previous=new State(timestampNs,quat,delayUs);
        history.addLast(previous);
        while(!history.isEmpty()&&timestampNs-history.peekFirst().timeNs>1_200_000_000L)
            history.removeFirst();
        if(history.size()<2)
            return new Result("IMU_AQUECENDO",true,false,false,false,
                speedEma,0,Double.NaN);
        // Use ~600ms of motion, not one single short step.
        State reference=history.peekFirst();
        for(State s:history){
            if(timestampNs-s.timeNs<=600_000_000L){reference=s;break;}
        }
        double rotation=angleDegrees(reference.q,quat);
        double elapsed=Math.max(.001,(timestampNs-reference.timeNs)/1.e9);
        boolean moving=rotation>3.0||speedEma>12.0;
        double bound=2*0.1426/343*1.e6*Math.sin(Math.toRadians(rotation)/2);
        // 45us slack allows sample quantisation/HAL bias; *not* a proof of motion model.
        boolean plausible=Math.abs(delayUs-reference.delay)<=bound+45.0;
        boolean stationary=!moving && rotation<=2.0;
        String status=stationary?"PARADO":plausible?
            "ROTACAO_PLAUSIVEL":"ROTACAO_INCOMPATIVEL";
        return new Result(status,true,moving,plausible,stationary,
            speedEma,rotation,bound);
    }
    public void reset(){history.clear();previous=null;speedEma=0;}
    public static double angleDegrees(double[] a,double[] b){
        double da=0,na=0,nb=0;
        for(int i=0;i<4;i++){da+=a[i]*b[i];na+=a[i]*a[i];nb+=b[i]*b[i];}
        if(na<1e-14||nb<1e-14)return 180;
        double dot=Math.min(1.0,Math.abs(da/Math.sqrt(na*nb)));
        return Math.toDegrees(2*Math.acos(dot));
    }
}
