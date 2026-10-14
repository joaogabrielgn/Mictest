package com.acousticdirection.lab;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Two-channel audiovisual timing calibration for ONE selected stationary source.
 * The reference projection is computed independently from ARCore camera+sensor
 * pose and user-marked source, NOT from the acoustic estimator.
 *
 * CALIBRATION uses training observations to fit offset+gain; VALIDATION uses
 * fresh, separately recorded observations. No ML or saved global phone profile.
 */
public final class AudiovisualCalibration {
    public static final class Sample {
        public final long timeNs;
        public final double projection,delayUs;
        public final double quality;
        Sample(long t,double p,double delay,double q){
            timeNs=t;projection=p;delayUs=delay;quality=q;
        }
    }
    public static final class Fit {
        public final boolean valid;
        public final String status;
        public final double offsetUs,gainUs,trainingMaeUs;
        public final int trainingCount;
        public final double projectionRange;
        Fit(boolean valid,String status,double offset,double gain,
            double mae,int count,double range){
            this.valid=valid;this.status=status;offsetUs=offset;
            gainUs=gain;trainingMaeUs=mae;trainingCount=count;
            projectionRange=range;
        }
        public double predict(double projection){return offsetUs+gainUs*projection;}
        public double fromDelay(double delay){return (delay-offsetUs)/gainUs;}
        public String display(){
            if(!valid)return "Calibração: "+status+" | amostras="+trainingCount+
                String.format(Locale.US," | diversidade %.2f",projectionRange);
            return String.format(Locale.US,
                "Perfil audiovisual: offset %+.1f µs | ganho %+.1f µs\n"+
                "MAE de ajuste %.1f µs | %d observações | cobertura %.2f",
                offsetUs,gainUs,trainingMaeUs,trainingCount,projectionRange);
        }
    }
    public static final class Validation {
        public final int count;
        public final double maeUs,baselineMaeUs,range;
        public final String status;
        Validation(int n,double mae,double base,double range,String status){
            count=n;maeUs=mae;baselineMaeUs=base;this.range=range;
            this.status=status;
        }
        public String display(){
            return String.format(Locale.US,
                "VALIDAÇÃO INDEPENDENTE: %s | %d medidas\nMAE %.1f µs | referência ingênua %.1f µs",
                status,count,maeUs,baselineMaeUs);
        }
    }
    private final ArrayList<Sample> training=new ArrayList<>();
    private final ArrayList<Sample> validation=new ArrayList<>();
    private Fit frozen=null;
    private long lastTrainNs=-1,lastValidationNs=-1;
    public synchronized void clear(){
        training.clear();validation.clear();frozen=null;
        lastTrainNs=-1;lastValidationNs=-1;
    }
    public synchronized boolean isValidation(){return frozen!=null&&frozen.valid;}
    public synchronized int trainSize(){return training.size();}
    public synchronized int validationSize(){return validation.size();}
    public synchronized boolean add(long t,double projection,double delay,double quality){
        if(t<=0||!Double.isFinite(projection)||Math.abs(projection)>1.02 ||
            !Double.isFinite(delay)||Math.abs(delay)>800 ||
            !Double.isFinite(quality)||quality<.12)return false;
        if(isValidation()){
            if(lastValidationNs>=0&&t-lastValidationNs<170_000_000L)return false;
            validation.add(new Sample(t,projection,delay,quality));
            if(validation.size()>300)validation.remove(0);
            lastValidationNs=t;
        }else{
            if(lastTrainNs>=0&&t-lastTrainNs<170_000_000L)return false;
            training.add(new Sample(t,projection,delay,quality));
            if(training.size()>350)training.remove(0);
            lastTrainNs=t;
        }
        return true;
    }
    public synchronized Fit preview(){return fit(training);}
    public synchronized Fit freeze(){
        Fit f=fit(training);
        if(f.valid){frozen=f;validation.clear();lastValidationNs=-1;}
        return f;
    }
    public synchronized Fit fitted(){return frozen;}
    public synchronized Validation validate(){
        if(frozen==null || !frozen.valid)
            return new Validation(0,Double.NaN,Double.NaN,0,"AINDA_NAO_CALIBRADO");
        if(validation.size()<12)
            return new Validation(validation.size(),Double.NaN,Double.NaN,
                0,"COLETANDO_DADOS_NOVOS");
        double min=1,max=-1,err=0,baseline=0;
        for(Sample sample:validation){
            min=Math.min(min,sample.projection);max=Math.max(max,sample.projection);
            err+=Math.abs(sample.delayUs-frozen.predict(sample.projection));
            baseline+=Math.abs(sample.delayUs-frozen.offsetUs);
        }
        double mae=err/validation.size(),ref=baseline/validation.size();
        String status=(max-min)<.4?"POUCA_VARIACAO_ANGULAR":
            (mae<=85 && mae<ref*.78)?"VALIDACAO_TDOA_OK":"ERRO_TDOA_ELEVADO";
        return new Validation(validation.size(),mae,ref,max-min,status);
    }
    private static Fit fit(ArrayList<Sample> data){
        int n=data.size();
        double min=1,max=-1;
        for(Sample s:data){min=Math.min(min,s.projection);max=Math.max(max,s.projection);}
        double span=n>0?max-min:0;
        if(n<24 || span<.55)return new Fit(false,
            n<24?"COLETANDO_MEDIDAS":"GIRE_E_INCLINE_EM_MAIS_DIRECOES",
            Double.NaN,Double.NaN,Double.NaN,n,span);
        double offset=0,gain=450;
        // Huber reweighted linear regression, 5 iterations.
        for(int iter=0;iter<6;iter++){
            double sw=0,sx=0,sy=0,sxx=0,sxy=0;
            for(Sample s:data){
                double error=s.delayUs-(offset+gain*s.projection);
                double weight=Math.max(.2,Math.min(1.0,75.0/Math.max(1e-6,Math.abs(error))));
                weight*=Math.max(.25,Math.min(1.0,s.quality));
                sw+=weight;sx+=weight*s.projection;sy+=weight*s.delayUs;
                sxx+=weight*s.projection*s.projection;
                sxy+=weight*s.projection*s.delayUs;
            }
            double determinant=sw*sxx-sx*sx;
            if(determinant<1e-5)return new Fit(false,"SEM_DIVERSIDADE_GEOMETRICA",
                Double.NaN,Double.NaN,Double.NaN,n,span);
            gain=(sw*sxy-sx*sy)/determinant;
            offset=(sy-gain*sx)/sw;
        }
        double mae=0;
        for(Sample s:data)mae+=Math.abs(s.delayUs-(offset+gain*s.projection));
        mae/=n;
        // Abs gain, not signed gain, because mic channel routing could reverse.
        boolean sane=Math.abs(gain)>170&&Math.abs(gain)<850&&
            Math.abs(offset)<270&&mae<125;
        return new Fit(sane,sane?"AJUSTE_PILOTO":"GANHO_OU_ERRO_INCOMPATIVEL",
            offset,gain,mae,n,span);
    }
    public synchronized String exportRows(){
        StringBuilder out=new StringBuilder();
        out.append("phase,boottime_ns,known_projection,measured_delay_us,band_quality\n");
        for(Sample s:training)append(out,"TRAIN",s);
        for(Sample s:validation)append(out,"VALID",s);
        return out.toString();
    }
    private static void append(StringBuilder out,String phase,Sample s){
        out.append(String.format(Locale.US,"%s,%d,%.7f,%.4f,%.5f\n",
            phase,s.timeNs,s.projection,s.delayUs,s.quality));
    }
}
