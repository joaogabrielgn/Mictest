package com.acousticdirection.lab;

import java.util.ArrayDeque;

/**
 * Streaming wideband transient detector. Operates on interleaved PCM frames and
 * emits a 2048-frame window around each onset, INCLUDING buffers boundaries.
 * Trigger uses short-time mean absolute first differences (on any channel)
 * against an adaptive background. It is not a voice/sustained-sound detector.
 */
public final class EventDetector {
    public static final int WINDOW = 2048;
    private static final int PRE = 256;
    private static final int POST = WINDOW - PRE;
    private static final int RING_FRAMES = 8192;
    private static final int BLOCK = 64;

    public interface Listener { void onEvent(Event event); }
    public static final class Event {
        public final long onsetFrame, windowStartFrame;
        public final short[] pcm;
        public final double triggerRatio;
        Event(long onset, short[] samples, double ratio) {
            onsetFrame = onset; windowStartFrame = onset-PRE;
            pcm = samples; triggerRatio=ratio;
        }
    }
    private static final class Pending {
        final long onset;
        final double ratio;
        Pending(long onset,double ratio){this.onset=onset;this.ratio=ratio;}
    }

    private final int channels, sampleRate, refractoryFrames;
    private final short[] ring, previous;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private long processed=0, lastTrigger=-99999999;
    private double background=0.0003, sumDiff=0;
    private int blockCount=0;
    private int detected=0, emitted=0, overflow=0, suppressed=0;

    public EventDetector(int channels, int sampleRate) {
        if(channels<1 || channels>4 || sampleRate<8000) throw new IllegalArgumentException("Invalid audio layout");
        this.channels=channels;this.sampleRate=sampleRate;
        ring=new short[RING_FRAMES*channels];previous=new short[channels];
        refractoryFrames=Math.max(1, (int)(sampleRate*.35));
    }

    public void feed(short[] data,int count,Listener listener) {
        if(count<0 || count>data.length || count%channels!=0)throw new IllegalArgumentException("Frame-aligned PCM required");
        int frames=count/channels;
        for(int i=0;i<frames;i++){
            int input=i*channels;
            int ringOffset=(int)(processed%RING_FRAMES)*channels;
            double transientAmp=0.0;
            for(int ch=0;ch<channels;ch++){
                short s=data[input+ch];
                ring[ringOffset+ch]=s;
                double derivative=Math.abs(((int)s-(int)previous[ch])/32768.0);
                transientAmp=Math.max(transientAmp,derivative);
                previous[ch]=s;
            }
            sumDiff+=transientAmp;blockCount++;
            if(blockCount>=BLOCK) {
                double mean=sumDiff/blockCount;
                double noise=Math.max(background,0.00015);
                double ratio=mean/noise;
                // Gate against faint thermal noise; insist on sharp onset rather than periodic low-amplitude hiss.
                boolean strongTransient = mean>0.0030 && ratio>5.0 && processed>PRE+BLOCK;
                boolean cooldown = processed-lastTrigger<=refractoryFrames;
                if(strongTransient && cooldown) suppressed++;
                boolean hit = strongTransient && !cooldown && pending.size()<12;
                if(hit) {
                    long onset=processed-(BLOCK/2);
                    pending.addLast(new Pending(onset,ratio));
                    lastTrigger=processed;
                    detected++;
                } else if (pending.size()>=12) overflow++;
                // Update background only with non-transient blocks; avoid slowly 'learning' an impact.
                if(ratio<3.0)background=.98*background+.02*Math.max(mean,0.00005);
                sumDiff=0;blockCount=0;
            }
            processed++;
            while(!pending.isEmpty() && processed>=pending.peekFirst().onset+POST) {
                Pending p=pending.removeFirst();
                long start=p.onset-PRE;
                if(processed-start>RING_FRAMES){overflow++;continue;}
                short[] copy=new short[WINDOW*channels];
                for(int k=0;k<WINDOW;k++){
                    int readOffset=(int)((start+k)%RING_FRAMES)*channels;
                    System.arraycopy(ring,readOffset,copy,k*channels,channels);
                }
                listener.onEvent(new Event(p.onset,copy,p.ratio));
                emitted++;
            }
        }
    }
    public long framesConsumed(){return processed;}
    public int detectedCount(){return detected;}
    public int emittedCount(){return emitted;}
    public int overflowCount(){return overflow;}
    public int pendingCount(){return pending.size();}
    /** Number of transient-analysis blocks blocked by 350 ms refractory; not events. */
    public int suppressedBlocks(){return suppressed;}
}
