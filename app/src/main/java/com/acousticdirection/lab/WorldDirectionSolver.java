package com.acousticdirection.lab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * EXPERIMENTAL stationary-source reconstruction while rotating the phone.
 *
 * Each stereo TDOA provides ONE scalar: projection of a world-space source
 * direction onto the phone's microphone baseline. Many differently oriented
 * baselines permit a least-squares direction only if they span 3 dimensions.
 *
 * Assumptions (not proven for the Samsung HAL): far-field source, unchanged
 * source position, user-provided mic-end calibration, channel time alignment
 * stable with rotation, and device quaternion mapped to a stable world frame.
 * Reject rank-deficient, unstable or inconsistent solutions. Never infer range.
 */
public final class WorldDirectionSolver {
    // SM-S931B Android MicrophoneInfo: back_mic - built_in_mic, meters.
    // This is a tentative physical baseline, NOT verified channel routing.
    private static final double[] DEVICE_BASELINE=normalize(new double[]{
        .0546-.0269, .1456-.0058, .00415-.0079
    });
    private static final long MAX_AGE_NS=45_000_000_000L;
    private static final long MIN_SPACING_NS=130_000_000L;
    private static final int MAX_POINTS=300;
    private final ArrayDeque<Point> points=new ArrayDeque<>();
    private long lastPointNs=-1;
    private static final class Point {
        final long t;final double[] axis;final double measured;
        Point(long t,double[] axis,double projection){
            this.t=t;this.axis=axis;this.measured=projection;
        }
    }
    public static final class Result {
        public final boolean available;
        public final String status;
        public final int observations;
        public final double durationSec,spanDegrees,rankRatio,rmsProjection;
        public final double[] world,device;
        public final double horizontalCameraDeg,verticalCameraDeg;
        public final boolean rearHemisphere;
        Result(String status,boolean available,int count,double duration,double spread,
               double rankRatio,double rms,double[] world,double[] device,
               double horizontal,double vertical,boolean rear){
            this.status=status;this.available=available;observations=count;
            durationSec=duration;spanDegrees=spread;this.rankRatio=rankRatio;
            rmsProjection=rms;this.world=world;this.device=device;
            horizontalCameraDeg=horizontal;verticalCameraDeg=vertical;
            rearHemisphere=rear;
        }
        public String display(){
            if(!available)
                return String.format(Locale.US,
                    "3D experimental: %s | amostras=%d, varredura=%.0f° | RMS=%s",
                    status,observations,spanDegrees,Double.isFinite(rmsProjection)?
                    String.format(Locale.US,"%.3f",rmsProjection):"—");
            return String.format(Locale.US,
                "3D EXPERIMENTAL: horizontal %+.1f°, vertical %+.1f° (referência da traseira)\n"+
                "Câmera traseira: %s | consistência RMS %.3f | cobertura %.0f°",
                horizontalCameraDeg,verticalCameraDeg,
                rearHemisphere?"hemisfério traseiro":"hemisfério oposto",
                rmsProjection,spanDegrees);
        }
    }
    private static Result unavailable(String status,int n,double duration,
                                      double span,double rank,double rms){
        return new Result(status,false,n,duration,span,rank,rms,
            new double[]{Double.NaN,Double.NaN,Double.NaN},
            new double[]{Double.NaN,Double.NaN,Double.NaN},
            Double.NaN,Double.NaN,false);
    }
    public void reset(){points.clear();lastPointNs=-1;}
    public int points(){return points.size();}
    public Result add(long timeNs,double[] quaternion,double delayUs,boolean accepted){
        if(accepted&&timeNs>0&&Double.isFinite(delayUs)&&quaternion!=null&&
           quaternion.length==4&&
           (lastPointNs<0||timeNs-lastPointNs>=MIN_SPACING_NS)){
            EmpiricalAxis.Axis score=EmpiricalAxis.estimate(delayUs);
            // Read outside the literal end anchors with modest headroom; discard
            // huge HAL artefacts instead of quietly clipping everything.
            if(Double.isFinite(score.rawScore) && Math.abs(score.rawScore)<=1.15){
                if(lastPointNs>timeNs)reset();
                points.addLast(new Point(timeNs,
                    rotate(quaternion,DEVICE_BASELINE),
                    Math.max(-1,Math.min(1,score.rawScore))));
                lastPointNs=timeNs;
            }
        }
        if(timeNs>0){
            while(!points.isEmpty() && timeNs-points.peekFirst().t>MAX_AGE_NS)
                points.removeFirst();
        }
        while(points.size()>MAX_POINTS)points.removeFirst();
        return solve(quaternion);
    }
    public Result solve(double[] nowQuaternion){
        int n=points.size();
        double elapsed=n>1?(points.getLast().t-points.getFirst().t)/1e9:0;
        if(n<20||elapsed<3.0)
            return unavailable("COLETANDO_ROTACOES",n,elapsed,0,Double.NaN,Double.NaN);
        ArrayList<Point> p=new ArrayList<>(points);
        double spread=0;
        for(int i=0;i<p.size();i+=2)
            for(int j=i+2;j<p.size();j+=3){
                double dot=dot(p.get(i).axis,p.get(j).axis);
                spread=Math.max(spread,Math.toDegrees(Math.acos(clamp(dot,-1,1))));
            }
        if(spread<40)
            return unavailable("VARREDURA_ANGULAR_INSUFICIENTE",n,elapsed,spread,Double.NaN,Double.NaN);
        double[][] gram=new double[3][3];
        double[] rhs=new double[3];
        for(Point point:p)accumulate(gram,rhs,point,1);
        double[] eigen=eigenvalues(gram);
        double rank=Math.max(0,eigen[0])/Math.max(eigen[2],1e-12);
        if(rank<.022)
            return unavailable("EIXOS_NAO_COBREM_3D",n,elapsed,spread,rank,Double.NaN);
        double[] solution=solve3(gram,rhs);
        if(solution==null)
            return unavailable("SISTEMA_SINGULAR",n,elapsed,spread,rank,Double.NaN);
        // Huber robustness: retry fit by downweighting discordant TDOAs.
        for(int iter=0;iter<3;iter++){
            double[][] a=new double[3][3];double[] b=new double[3];
            for(Point point:p){
                double residual=point.measured-dot(point.axis,solution);
                double weight=Math.min(1,.12/Math.max(Math.abs(residual),1e-9));
                accumulate(a,b,point,weight);
            }
            double[] updated=solve3(a,b);
            if(updated==null)break;
            solution=updated;
        }
        double magnitude=Math.sqrt(dot(solution,solution));
        if(magnitude<.55 || magnitude>1.42)
            return unavailable("ESCALA_INCOMPATIVEL",n,elapsed,spread,rank,Double.NaN);
        double[] world=normalize(solution);
        double sum=0;
        for(Point point:p){
            double error=point.measured-dot(point.axis,world);
            sum+=error*error;
        }
        double rms=Math.sqrt(sum/n);
        if(rms>.18)
            return unavailable("AJUSTE_INCONSISTENTE",n,elapsed,spread,rank,rms);
        if(nowQuaternion==null || nowQuaternion.length!=4)
            return unavailable("SEM_POSE_ATUAL",n,elapsed,spread,rank,rms);
        double[] device=rotate(conjugate(nowQuaternion),world);
        // Camera rear optical direction in Android device coordinates is -Z.
        // These angles use device axes, not landscape screen pixel coordinates.
        double horizontal=Math.toDegrees(Math.atan2(device[0],-device[2]));
        double vertical=Math.toDegrees(Math.atan2(device[1],
            Math.hypot(device[0],device[2])));
        return new Result("DIRECAO_3D_EXPERIMENTAL",true,n,elapsed,spread,rank,rms,
            world,device,horizontal,vertical,device[2]<0);
    }
    public static double[] microphoneAxisDevice(){return DEVICE_BASELINE.clone();}
    public static double[] rotate(double[] q,double[] vector){
        double norm=Math.sqrt(dot(q,q));
        if(norm<1e-10)return new double[]{Double.NaN,Double.NaN,Double.NaN};
        double w=q[0]/norm,x=q[1]/norm,y=q[2]/norm,z=q[3]/norm;
        double vx=vector[0],vy=vector[1],vz=vector[2];
        double tx=2*(y*vz-z*vy),ty=2*(z*vx-x*vz),tz=2*(x*vy-y*vx);
        return new double[]{vx+w*tx+(y*tz-z*ty),
                            vy+w*ty+(z*tx-x*tz),
                            vz+w*tz+(x*ty-y*tx)};
    }
    private static double[] conjugate(double[] q){return new double[]{q[0],-q[1],-q[2],-q[3]};}
    private static double dot(double[] a,double[] b){
        double r=0;for(int i=0;i<a.length;i++)r+=a[i]*b[i];return r;
    }
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    private static double[] normalize(double[] v){
        double n=Math.sqrt(dot(v,v));if(n<1e-12)return v.clone();
        double[] r=v.clone();for(int i=0;i<r.length;i++)r[i]/=n;return r;
    }
    private static void accumulate(double[][] matrix,double[] rhs,Point point,double weight){
        for(int i=0;i<3;i++){
            rhs[i]+=weight*point.axis[i]*point.measured;
            for(int j=0;j<3;j++)
                matrix[i][j]+=weight*point.axis[i]*point.axis[j];
        }
    }
    private static double[] solve3(double[][] a,double[] b){
        double[][] v=new double[3][4];
        for(int i=0;i<3;i++){
            System.arraycopy(a[i],0,v[i],0,3);v[i][3]=b[i];
        }
        for(int k=0;k<3;k++){
            int pivot=k;
            for(int i=k+1;i<3;i++)if(Math.abs(v[i][k])>Math.abs(v[pivot][k]))pivot=i;
            if(Math.abs(v[pivot][k])<1.e-9)return null;
            double[] swap=v[k];v[k]=v[pivot];v[pivot]=swap;
            double denom=v[k][k];
            for(int j=k;j<4;j++)v[k][j]/=denom;
            for(int i=0;i<3;i++)if(i!=k){
                double f=v[i][k];
                for(int j=k;j<4;j++)v[i][j]-=f*v[k][j];
            }
        }
        return new double[]{v[0][3],v[1][3],v[2][3]};
    }
    private static double[] eigenvalues(double[][] a){
        double[][] m=new double[3][3];
        for(int i=0;i<3;i++)m[i]=a[i].clone();
        for(int sweep=0;sweep<25;sweep++){
            int p=0,q=1;
            for(int i=0;i<3;i++)for(int j=i+1;j<3;j++)
                if(Math.abs(m[i][j])>Math.abs(m[p][q])){p=i;q=j;}
            if(Math.abs(m[p][q])<1.e-11)break;
            double theta=.5*Math.atan2(2*m[p][q],m[q][q]-m[p][p]);
            double cs=Math.cos(theta),sn=Math.sin(theta);
            double app=m[p][p],aqq=m[q][q],apq=m[p][q];
            m[p][p]=cs*cs*app-2*cs*sn*apq+sn*sn*aqq;
            m[q][q]=sn*sn*app+2*cs*sn*apq+cs*cs*aqq;
            m[p][q]=m[q][p]=0;
            for(int r=0;r<3;r++)if(r!=p&&r!=q){
                double mrp=m[r][p],mrq=m[r][q];
                m[r][p]=m[p][r]=cs*mrp-sn*mrq;
                m[r][q]=m[q][r]=sn*mrp+cs*mrq;
            }
        }
        double[] result=new double[]{m[0][0],m[1][1],m[2][2]};
        Arrays.sort(result);
        return result;
    }
}
