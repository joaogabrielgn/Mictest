package com.acousticdirection.lab;

/**
 * Attempts to locate the FIRST audible rising edge in each PCM channel.
 * This is a heuristic observation, not a ground-truth direct-path detector.
 * An early reflection or asymmetric Samsung processing can mislead it.
 */
public final class OnsetInspector {
    private OnsetInspector() {}
    public static final class Pair {
        public final int onset0, onsetB;
        public final double delayUs;
        public final String quality;
        Pair(int a,int b,double d,String quality){
            onset0=a;onsetB=b;delayUs=d;this.quality=quality;
        }
        public boolean usable(){return "ONSET_OK".equals(quality);}
    }
    /** Input is a 2048-frame interleaved event with trigger close to frame 256. */
    public static Pair examine(short[] pcm,int channels,int channelB,int rate){
        if(pcm==null||channels<2||channelB>=channels||rate<=0||pcm.length<512*channels)
            return new Pair(-1,-1,Double.NaN,"SEM_JANELA");
        int a=firstRise(pcm,channels,0),b=firstRise(pcm,channels,channelB);
        if(a<0||b<0)return new Pair(a,b,Double.NaN,"ONSET_NAO_ENCONTRADO");
        double us=(b-a)*1.0e6/rate;
        return new Pair(a,b,us,"ONSET_OK");
    }
    private static int firstRise(short[] pcm,int channels,int channel){
        int frames=pcm.length/channels;
        // First 128 frames should precede the detected event.
        int baselineEnd=Math.min(128,frames/4);
        double baseline=0;
        for(int i=1;i<baselineEnd;i++){
            baseline+=Math.abs((int)pcm[i*channels+channel]-
                (int)pcm[(i-1)*channels+channel])/32768.0;
        }
        baseline/=Math.max(1,baselineEnd-1);
        double threshold=Math.max(.0015,baseline*6.0);
        // Require a local accumulation of derivative energy to reduce random single-sample noise.
        int last=Math.min(frames-9,520);
        for(int i=baselineEnd;i<=last;i++){
            double sum=0;
            for(int j=i;j<i+8;j++)
                sum+=Math.abs((int)pcm[j*channels+channel]-
                    (int)pcm[(j-1)*channels+channel])/32768.0;
            if(sum >= threshold*6.0){
                return i;
            }
        }
        return -1;
    }
}
