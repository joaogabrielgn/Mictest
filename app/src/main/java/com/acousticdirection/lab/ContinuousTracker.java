package com.acousticdirection.lab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Experimental continuous TDOA. A 4096-frame Hann window with a 1024-frame
 * hop (85.3ms / 21.3ms at 48kHz). It never requires clicks/onsets.
 *
 * Bandwise PHAT + energy/peak checks estimate the strongest REPEATABLE
 * acoustic component, not individual sound sources. In multi-source scenes
 * "loudest source" and 3D DOA cannot be established with this algorithm.
 */
public final class ContinuousTracker {
    public static final int WINDOW=4096, HOP=1024;
    private static final String[] NAMES={"GRAVE","MEDIO","AGUDO"};
    private static final double[][] BANDS={{180,950},{950,3000},{3000,8500}};
    private final int rate,channels,ringSize;
    private final short[] ring;
    private final ArrayDeque<Double> history=new ArrayDeque<>();
    private long frames=0,lastWindowFrame=-1;
    private double smooth=Double.NaN;
    private long lastGoodFrame=-1;
    private final double searchUsLimit=0.1426/343.0*1e6+20.0/48000.0*1e6;

    public interface Listener {void onWindow(Window window);}
    public static final class Band {
        public final String name;
        public final double delayUs,energyDb,peakRatio,contrast;
        public final boolean valid;
        public final String reason;
        Band(String name,double delayUs,double energyDb,double peak,double contrast,
             boolean valid,String reason){
            this.name=name;this.delayUs=delayUs;this.energyDb=energyDb;
            this.peakRatio=peak;this.contrast=contrast;this.valid=valid;this.reason=reason;
        }
    }
    public static final class Window {
        public final long centerFrame;
        public final double levelDb,levelDifferenceDb,flatness,rawDelayUs,smoothedDelayUs;
        public final int agreeingBands,validBands;
        public final boolean reliable,clipped;
        public final String status,dominantBand;
        public final Band[] bands;
        Window(long center,double db,double diff,double flatness,double raw,double smooth,
               int agree,int valid,boolean reliable,boolean clipped,String status,
               String dominant,Band[] bands){
            centerFrame=center;levelDb=db;levelDifferenceDb=diff;this.flatness=flatness;
            rawDelayUs=raw;smoothedDelayUs=smooth;agreeingBands=agree;
            validBands=valid;this.reliable=reliable;this.clipped=clipped;
            this.status=status;dominantBand=dominant;this.bands=bands;
        }
        public String summary(){
            return String.format(Locale.US,
                "SOM CONTÍNUO | %s | banda %s\nnível %.1f dBFS | dL %+.1f dB | flatness %.3f\nTDOA %s | suavizado %s | bandas %d/%d",
                status,dominantBand,levelDb,levelDifferenceDb,flatness,
                Double.isFinite(rawDelayUs)?String.format(Locale.US,"%+.1f us",rawDelayUs):"indeterminado",
                Double.isFinite(smoothedDelayUs)?String.format(Locale.US,"%+.1f us",smoothedDelayUs):"indeterminado",
                agreeingBands,validBands);
        }
    }
    public ContinuousTracker(int channels,int sampleRate){
        if(channels<2||channels>4||sampleRate<8000)throw new IllegalArgumentException("Need >=2 channels");
        this.channels=channels;rate=sampleRate;ringSize=WINDOW;
        ring=new short[ringSize*channels];
    }
    public void feed(short[] samples,int count,Listener callback){
        if(count<0||count>samples.length||count%channels!=0)throw new IllegalArgumentException("Invalid PCM");
        for(int i=0;i<count/channels;i++){
            int index=(int)(frames%ringSize)*channels;
            System.arraycopy(samples,i*channels,ring,index,channels);
            frames++;
            if(frames>=WINDOW && (frames-WINDOW)%HOP==0){
                Window w=analyzeCurrent();
                lastWindowFrame=frames;
                callback.onWindow(w);
            }
        }
    }
    public long totalFrames(){return frames;}
    private Window analyzeCurrent(){
        double[] x=new double[WINDOW],y=new double[WINDOW];
        double meanX=0,meanY=0, clip=0;
        long start=frames-WINDOW;
        for(int i=0;i<WINDOW;i++){
            int j=(int)((start+i)%ringSize)*channels;
            x[i]=ring[j]/32768.0; y[i]=ring[j+1]/32768.0;
            meanX+=x[i];meanY+=y[i];
            if(Math.abs(x[i])>=.995||Math.abs(y[i])>=.995)clip++;
        }
        meanX/=WINDOW;meanY/=WINDOW;
        double powerX=0,powerY=0;
        double[] xr=new double[WINDOW],xi=new double[WINDOW],
                 yr=new double[WINDOW],yi=new double[WINDOW];
        for(int i=0;i<WINDOW;i++){
            double a=x[i]-meanX,b=y[i]-meanY;
            powerX+=a*a;powerY+=b*b;
            double hann=.5-.5*Math.cos(2*Math.PI*i/(WINDOW-1));
            xr[i]=a*hann;yr[i]=b*hann;
        }
        double rmsX=Math.sqrt(powerX/WINDOW),rmsY=Math.sqrt(powerY/WINDOW);
        double dbX=20*Math.log10(Math.max(rmsX,1e-10));
        double dbY=20*Math.log10(Math.max(rmsY,1e-10));
        double levelDb=Math.max(dbX,dbY),diffDb=dbY-dbX;
        boolean clipDetected=clip>0;
        SpatialDsp.fft(xr,xi,false);
        SpatialDsp.fft(yr,yi,false);
        double[] spectrum=new double[WINDOW/2+1];
        double bandPowerSum=0;
        double logSum=0,arithSum=0;
        int flatBins=0;
        double[] bandPower=new double[BANDS.length];
        for(int k=1;k<=WINDOW/2;k++){
            double hz=k*(double)rate/WINDOW;
            double e=xr[k]*xr[k]+xi[k]*xi[k]+yr[k]*yr[k]+yi[k]*yi[k];
            spectrum[k]=e;
            if(hz>=180 && hz<=Math.min(8500,rate*.45)){
                arithSum+=e;logSum+=Math.log(Math.max(e,1e-20));flatBins++;
            }
            for(int b=0;b<BANDS.length;b++)
                if(hz>=BANDS[b][0]&&hz<BANDS[b][1]){bandPower[b]+=e;bandPowerSum+=e;break;}
        }
        double flatness=flatBins>0?
            Math.exp(logSum/flatBins)/Math.max(arithSum/flatBins,1e-20):0;
        // At least a weak audio signal is needed to interpret any direction.
        boolean quiet=levelDb < -54.0;
        int maxLag=Math.min(90,(int)Math.ceil(rate*.1426/343.0)+
            Math.max(8,(int)Math.ceil(rate*.00042)));
        Band[] estimates=new Band[BANDS.length];
        int dominant=0;
        for(int b=1;b<BANDS.length;b++) if(bandPower[b]>bandPower[dominant]) dominant=b;
        ArrayList<Double> candidates=new ArrayList<>();
        ArrayList<Double> weights=new ArrayList<>();
        int validCount=0;
        for(int b=0;b<BANDS.length;b++){
            String name=NAMES[b];
            double fraction=bandPower[b]/Math.max(bandPowerSum,1e-20);
            double bandDb=10*Math.log10(Math.max(bandPower[b],1e-20));
            if(quiet||fraction<.025){
                estimates[b]=new Band(name,Double.NaN,bandDb,0,0,false,
                    quiet?"SILENCIO":"ENERGIA_INSUFICIENTE");
                continue;
            }
            double[] re=new double[WINDOW],im=new double[WINDOW];
            for(int k=1;k<WINDOW;k++){
                double hz=Math.min(k,WINDOW-k)*(double)rate/WINDOW;
                if(hz<BANDS[b][0]||hz>=BANDS[b][1])continue;
                double rr=xr[k]*yr[k]+xi[k]*yi[k];
                double ii=xi[k]*yr[k]-xr[k]*yi[k];
                double amp=Math.hypot(rr,ii);
                if(amp>1e-9){re[k]=rr/amp;im[k]=ii/amp;}
            }
            SpatialDsp.fft(re,im,true);
            int lag=0;
            double peak=-Double.MAX_VALUE,second=0,energy=0;
            for(int offset=-maxLag;offset<=maxLag;offset++){
                double value=re[(offset+WINDOW)%WINDOW];
                if(value>peak){peak=value;lag=offset;}
                energy+=value*value;
            }
            for(int offset=-maxLag;offset<=maxLag;offset++)
                if(Math.abs(offset-lag)>2)
                    second=Math.max(second,Math.abs(re[(offset+WINDOW)%WINDOW]));
            double bg=Math.sqrt(energy/(2*maxLag+1));
            double peakRatio=peak/Math.max(bg,1e-12);
            double contrast=Math.max(0,(peak-second)/Math.max(peak,1e-12));
            double interp=0;
            if(lag>-maxLag && lag<maxLag){
                double left=re[(lag-1+WINDOW)%WINDOW],right=re[(lag+1+WINDOW)%WINDOW];
                double denom=left-2*peak+right;
                if(Math.abs(denom)>1e-12)interp=
                    Math.max(-.5,Math.min(.5,.5*(left-right)/denom));
            }
            double delay=-(lag+interp)*1e6/rate;
            String reason="OK";
            if(clipDetected)reason="CLIPPING";
            else if(Math.abs(lag)==maxLag)reason="BORDA_BUSCA";
            else if(peakRatio<2.7||contrast<.065)reason="PICO_AMBIGUO";
            boolean valid=reason.equals("OK");
            estimates[b]=new Band(name,delay,bandDb,peakRatio,contrast,valid,reason);
            if(valid){validCount++;candidates.add(delay);weights.add(
                fraction*Math.max(1.0,Math.min(8,peakRatio)));}
        }
        double raw=Double.NaN;
        int agreeing=0;
        String status;
        boolean reliable=false;
        if(quiet) status="SILENCIO";
        else if(clipDetected) status="CLIPPING";
        else if(validCount==0)status="SEM_PICO_CONFIAVEL";
        else {
            // A two-band consensus is evidence of one dominant delay, not proof
            // of source separation. Select inlier cluster without averaging opposite DOAs.
            double bestWeight=-1;
            for(int i=0;i<candidates.size();i++){
                double wsum=0,dsum=0;
                int close=0;
                for(int j=0;j<candidates.size();j++){
                    if(Math.abs(candidates.get(i)-candidates.get(j))<=105){
                        wsum+=weights.get(j);
                        dsum+=weights.get(j)*candidates.get(j);
                        close++;
                    }
                }
                if((close>agreeing)||(close==agreeing && wsum>bestWeight)){
                    agreeing=close;bestWeight=wsum;raw=dsum/wsum;
                }
            }
            if(agreeing<2)status="FAIXA_UNICA_OU_DIVERGENTE";
            else if(flatness<.025)status="TONAL_AMBIGUO";
            else {
                status="MULTIBANDA_OK";
                reliable=true;
            }
        }
        double smoothed=updateSmoother(raw,reliable);
        return new Window(frames-WINDOW/2,levelDb,diffDb,flatness,raw,smoothed,
            agreeing,validCount,reliable,clipDetected,status,NAMES[dominant],estimates);
    }
    private double updateSmoother(double raw,boolean reliable){
        if(!reliable||!Double.isFinite(raw)){
            if(lastGoodFrame>=0 && frames-lastGoodFrame>rate*.6){
                smooth=Double.NaN;history.clear();
            }
            return Double.NaN; // never show stale direction for unreliable audio
        }
        lastGoodFrame=frames;
        history.addLast(raw);
        while(history.size()>7)history.removeFirst();
        ArrayList<Double> sorted=new ArrayList<>(history);
        Collections.sort(sorted);
        double median=sorted.get(sorted.size()/2);
        if(!Double.isFinite(smooth)||Math.abs(raw-smooth)>320)smooth=median;
        else smooth=.55*smooth+.45*median;
        return smooth;
    }
}
