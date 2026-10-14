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
    private static final String[] NAMES={"GRAVE","MEDIO","AGUDO","ADAPTATIVA"};
    private static final double[][] BANDS={{180,950},{950,3000},{3000,8500}};
    private final int rate,channels,ringSize;
    private final short[] ring;
    private final ArrayDeque<Double> history=new ArrayDeque<>();
    private final TemporalEvidence temporal;
    // Exponential cross-spectral averaging provides an *estimated* interchannel
    // magnitude-squared coherence. Overlapping windows are correlated.
    private final double[] crossReAvg=new double[WINDOW],crossImAvg=new double[WINDOW],
                           pxxAvg=new double[WINDOW],pyyAvg=new double[WINDOW];
    private int spectraSeen=0;
    private long frames=0,lastWindowFrame=-1;
    private double smooth=Double.NaN;
    private long lastGoodFrame=-1;
    private final double searchUsLimit=0.1426/343.0*1e6+20.0/48000.0*1e6;

    public interface Listener {void onWindow(Window window);}
    public static final class Band {
        public final String name;
        public final double delayUs,energyDb,peakRatio,contrast;
        public final double spectralCoherence, alternateDelayUs,alternatePeakRatio;
        public final int candidatePeaks;
        public final boolean valid;
        public final String reason;
        Band(String name,double delayUs,double energyDb,double peak,double contrast,
             boolean valid,String reason,double coherence,int nPeaks,
             double alternateDelay,double alternateRatio){
            this.name=name;this.delayUs=delayUs;this.energyDb=energyDb;
            this.peakRatio=peak;this.contrast=contrast;this.valid=valid;this.reason=reason;
            spectralCoherence=coherence;candidatePeaks=nPeaks;
            alternateDelayUs=alternateDelay;alternatePeakRatio=alternateRatio;
        }
    }
    public static final class Window {
        public final long centerFrame;
        public final double levelDb,levelDifferenceDb,flatness,rawDelayUs,smoothedDelayUs;
        public final int agreeingBands,validBands;
        public final boolean reliable,clipped,temporalStable;
        public final int temporalNeighbors;
        public final double temporalMedianUs,temporalMadUs,adaptiveLowHz,adaptiveHighHz;
        public final String confidenceClass;
        public final String status,dominantBand;
        public final Band[] bands;
        Window(long center,double db,double diff,double flatness,double raw,double smooth,
               int agree,int valid,boolean reliable,boolean clipped,String status,
               String dominant,Band[] bands,TemporalEvidence.Result evidence,double adaptiveLow,double adaptiveHigh){
            centerFrame=center;levelDb=db;levelDifferenceDb=diff;this.flatness=flatness;
            rawDelayUs=raw;smoothedDelayUs=smooth;agreeingBands=agree;
            validBands=valid;this.reliable=reliable;this.clipped=clipped;
            this.status=status;dominantBand=dominant;this.bands=bands;
            temporalStable=evidence.stable;temporalNeighbors=evidence.matched;
            temporalMedianUs=evidence.medianUs;temporalMadUs=evidence.madUs;
            adaptiveLowHz=adaptiveLow;adaptiveHighHz=adaptiveHigh;
            confidenceClass=reliable?"MULTIBANDA":temporalStable?"TEMPORAL_APENAS":
                Double.isFinite(raw)?"CANDIDATO":"INDETERMINADO";
        }
        public String summary(){
            return String.format(Locale.US,
                "SOM CONTÍNUO | %s (%s) | banda %s\nnível %.1f dBFS | dL %+.1f dB | flatness %.3f\nTDOA %s | suavizado %s | bandas %d/%d",
                status,confidenceClass,dominantBand,levelDb,levelDifferenceDb,flatness,
                Double.isFinite(rawDelayUs)?String.format(Locale.US,"%+.1f us",rawDelayUs):"indeterminado",
                Double.isFinite(smoothedDelayUs)?String.format(Locale.US,"%+.1f us",smoothedDelayUs):"indeterminado",
                agreeingBands,validBands);
        }
    }
    public ContinuousTracker(int channels,int sampleRate){
        if(channels<2||channels>4||sampleRate<8000)throw new IllegalArgumentException("Need >=2 channels");
        this.channels=channels;rate=sampleRate;ringSize=WINDOW;
        ring=new short[ringSize*channels];
        temporal=new TemporalEvidence(rate);
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
        double alpha=spectraSeen<2?.65:.28;
        for(int k=0;k<WINDOW;k++){
            double rr=xr[k]*yr[k]+xi[k]*yi[k];
            double ii=xi[k]*yr[k]-xr[k]*yi[k];
            double xx=xr[k]*xr[k]+xi[k]*xi[k];
            double yy=yr[k]*yr[k]+yi[k]*yi[k];
            if(spectraSeen==0){
                crossReAvg[k]=rr;crossImAvg[k]=ii;pxxAvg[k]=xx;pyyAvg[k]=yy;
            } else {
                crossReAvg[k]=(1-alpha)*crossReAvg[k]+alpha*rr;
                crossImAvg[k]=(1-alpha)*crossImAvg[k]+alpha*ii;
                pxxAvg[k]=(1-alpha)*pxxAvg[k]+alpha*xx;
                pyyAvg[k]=(1-alpha)*pyyAvg[k]+alpha*yy;
            }
        }
        spectraSeen++;
        double[] spectrum=new double[WINDOW/2+1];
        double bandPowerSum=0;
        double logSum=0,arithSum=0;
        int flatBins=0;
        double[] bandPower=new double[BANDS.length+1];
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
        double[] adaptiveHz=computeAdaptiveBounds(rate,spectrum);
        for(int k=1;k<=WINDOW/2;k++){
            double hz=k*(double)rate/WINDOW;
            if(hz>=adaptiveHz[0]&&hz<adaptiveHz[1])bandPower[BANDS.length]+=spectrum[k];
        }
        double flatness=flatBins>0?
            Math.exp(logSum/flatBins)/Math.max(arithSum/flatBins,1e-20):0;
        // At least a weak audio signal is needed to interpret any direction.
        boolean quiet=levelDb < -54.0;
        int maxLag=Math.min(90,(int)Math.ceil(rate*.1426/343.0)+
            Math.max(8,(int)Math.ceil(rate*.00042)));
        Band[] estimates=new Band[BANDS.length+1];
        int dominant=0;
        for(int b=1;b<BANDS.length;b++) if(bandPower[b]>bandPower[dominant]) dominant=b;
        ArrayList<Double> candidates=new ArrayList<>();
        ArrayList<Double> weights=new ArrayList<>();
        int validCount=0;
        for(int b=0;b<estimates.length;b++){
            String name=NAMES[b];
            double fraction=bandPower[b]/Math.max(bandPowerSum,1e-20);
            double bandDb=10*Math.log10(Math.max(bandPower[b],1e-20));
            if(quiet||fraction<.025){
                estimates[b]=new Band(name,Double.NaN,bandDb,0,0,false,
                    quiet?"SILENCIO":"ENERGIA_INSUFICIENTE",
                    0,0,Double.NaN,0);
                continue;
            }
            double[] re=new double[WINDOW],im=new double[WINDOW];
            double cohWeighted=0, cohTotal=0;
            int positivePeaks=0;
            for(int k=1;k<WINDOW;k++){
                double hz=Math.min(k,WINDOW-k)*(double)rate/WINDOW;
                double lower=b==BANDS.length?adaptiveHz[0]:BANDS[b][0];
                double upper=b==BANDS.length?adaptiveHz[1]:BANDS[b][1];
                if(hz<lower||hz>=upper)continue;
                double rr=crossReAvg[k],ii=crossImAvg[k];
                double amp=Math.hypot(rr,ii);
                double denominator=pxxAvg[k]*pyyAvg[k]+1e-20;
                double msc=Math.max(0,Math.min(1,(rr*rr+ii*ii)/denominator));
                double power=pxxAvg[k]+pyyAvg[k];
                cohWeighted+=msc*power;
                cohTotal+=power;
                if(amp>1e-9){
                    // A low-coherence bin contributes less, but is not erased
                    // solely because of the Samsung audio HAL.
                    double weight=.12+.88*Math.sqrt(msc);
                    re[k]=weight*rr/amp;im[k]=weight*ii/amp;
                }
            }
            SpatialDsp.fft(re,im,true);
            double coherence=cohWeighted/Math.max(cohTotal,1e-12);
            int lag=0;
            double peak=-Double.MAX_VALUE,second=0,energy=0;
            int alternativeLag=0;
            for(int offset=-maxLag;offset<=maxLag;offset++){
                double value=re[(offset+WINDOW)%WINDOW];
                if(value>peak){peak=value;lag=offset;}
                energy+=value*value;
            }
            for(int offset=-maxLag;offset<=maxLag;offset++){
                if(Math.abs(offset-lag)<=3)continue;
                double v=re[(offset+WINDOW)%WINDOW];
                if(v>second){second=v;alternativeLag=offset;}
            }
            for(int offset=-maxLag+1;offset<maxLag;offset++){
                double v=re[(offset+WINDOW)%WINDOW];
                if(v>=.55*peak && v>re[(offset-1+WINDOW)%WINDOW] &&
                   v>=re[(offset+1+WINDOW)%WINDOW])positivePeaks++;
            }
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
            else if(coherence<.12)reason="BAIXA_COHERENCIA";
            else if(peakRatio<(b==BANDS.length?2.4:2.05)||
                    contrast<(b==BANDS.length?.055:.045))reason="PICO_AMBIGUO";
            boolean valid=reason.equals("OK");
            estimates[b]=new Band(name,delay,bandDb,peakRatio,contrast,valid,reason,
                coherence,positivePeaks,
                -alternativeLag*1e6/rate,second/Math.max(peak,1e-12));
            if(valid && b<BANDS.length){validCount++;candidates.add(delay);weights.add(
                fraction*Math.max(1.0,Math.min(8,peakRatio)));}
        }
        double raw=Double.NaN;
        int agreeing=0;
        String status;
        boolean reliable=false;
        boolean tonal=flatness<.0015;
        if(quiet)status="SILENCIO";
        else if(clipDetected)status="CLIPPING";
        else {
            double bestWeight=-1;
            for(int i=0;i<candidates.size();i++){
                double wsum=0,dsum=0;
                int count=0;
                for(int j=0;j<candidates.size();j++){
                    if(Math.abs(candidates.get(i)-candidates.get(j))<=105){
                        wsum+=weights.get(j);
                        dsum+=weights.get(j)*candidates.get(j);
                        count++;
                    }
                }
                if(count>agreeing||(count==agreeing&&wsum>bestWeight)){
                    agreeing=count;bestWeight=wsum;raw=dsum/wsum;
                }
            }
            // Adaptive overlaps fixed bands: it never counts as a second
            // independent measurement for MULTIBANDA_OK.
            Band adaptive=estimates[BANDS.length];
            if(!Double.isFinite(raw)&&adaptive.valid)raw=adaptive.delayUs;
            if(!Double.isFinite(raw))status="SEM_PICO_CONFIAVEL";
            else if(tonal)status="TONAL_AMBIGUO";
            else if(agreeing>=2){status="MULTIBANDA_OK";reliable=true;}
            else status="CANDIDATO_UNICA_BANDA";
        }
        TemporalEvidence.Result evidence=temporal.observe(frames-WINDOW/2,
            raw,Double.isFinite(raw)&&!quiet&&!clipDetected,tonal);
        if(!reliable&&evidence.stable&&Double.isFinite(raw))
            status="TEMPORAL_ESTAVEL_BANDA_UNICA";
        double smoothed=updateSmoother(raw,reliable||evidence.stable);
        return new Window(frames-WINDOW/2,levelDb,diffDb,flatness,raw,smoothed,
            agreeing,validCount,reliable,clipDetected,status,NAMES[dominant],
            estimates,evidence,adaptiveHz[0],adaptiveHz[1]);
    }
    /**
     * Adaptive band from spectral support. Soft sqrt weights stop a single
     * narrow harmonic from completely setting its limits; width >=1300 Hz.
     * This band is not independent of the three fixed subbands.
     */
    private static double[] computeAdaptiveBounds(int sampleRate,double[] power){
        double maxHz=Math.min(8500,sampleRate*.45);
        int lo=(int)Math.ceil(250.0*WINDOW/sampleRate);
        int hi=Math.min(power.length-1,(int)Math.floor(maxHz*WINDOW/sampleRate));
        double total=0;
        for(int k=lo;k<=hi;k++)total+=Math.sqrt(Math.max(0,power[k]));
        if(total<1e-12)return new double[]{450,Math.min(maxHz,2500)};
        double cumulative=0,low=350,high=2500;
        boolean gotLow=false;
        for(int k=lo;k<=hi;k++){
            cumulative+=Math.sqrt(Math.max(0,power[k]));
            if(!gotLow&&cumulative>=total*.12){low=k*(double)sampleRate/WINDOW;gotLow=true;}
            if(cumulative>=total*.92){high=k*(double)sampleRate/WINDOW;break;}
        }
        double center=.5*(low+high);
        low=Math.max(250,Math.min(low,center-650));
        high=Math.min(maxHz,Math.max(high,center+650));
        if(high-low<1300){
            low=Math.max(250,center-650);
            high=Math.min(maxHz,center+650);
        }
        return new double[]{low,high};
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
