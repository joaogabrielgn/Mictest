package com.acousticdirection.lab;

/** Maps Android AudioRecord frame positions to BOOTTIME nanoseconds.
 * AudioTimestamp framePosition is explicitly in frames since stream start.
 * NEVER substitute a read-completion clock for a timestamp of captured samples.
 */
public final class AudioClock {
    public static final class Mapping {
        public final long timeNs;
        public final boolean valid;
        public final String status;
        Mapping(long timeNs,boolean valid,String status){
            this.timeNs=timeNs;this.valid=valid;this.status=status;
        }
    }
    private final int rate;
    private long framePosition, anchorTimeNs;
    private boolean ready=false;
    private int accepted=0, rejected=0;
    public AudioClock(int rate){
        if(rate<=0)throw new IllegalArgumentException("rate");
        this.rate=rate;
    }
    public void invalidate(){ready=false;}
    /** Caller supplies an already successful AudioRecord.getTimestamp(TIMEBASE_BOOTTIME). */
    public boolean update(long framePos,long timeNs,long framesRead,long nowNs){
        long estimatedCurrent=timeNs + Math.round((framesRead-framePos)*1.0e9/rate);
        boolean ok=framePos>=0 && timeNs>0 && framesRead>=0 &&
            framePos<=framesRead+rate && nowNs>timeNs-5_000_000L &&
            nowNs-estimatedCurrent < 2_000_000_000L &&
            estimatedCurrent<=nowNs+30_000_000L;
        if(ready && ok){
            long anticipated=anchorTimeNs + Math.round((framePos-framePosition)*1.0e9/rate);
            // Possible route switch / discontinuity / timestamp jitter above 25ms.
            if(Math.abs(timeNs-anticipated)>25_000_000L)ok=false;
        }
        if(!ok){ready=false;rejected++;return false;}
        framePosition=framePos;anchorTimeNs=timeNs;ready=true;accepted++;return true;
    }
    public Mapping timeForFrame(long position){
        if(!ready)return new Mapping(-1,false,"SEM_TIMESTAMP");
        long result=anchorTimeNs + Math.round((position-framePosition)*1.0e9/rate);
        return new Mapping(result,true,"OK");
    }
    public int accepted(){return accepted;}
    public int rejected(){return rejected;}
    public long anchorFrame(){return framePosition;}
    public long anchorTimeNs(){return anchorTimeNs;}
}
