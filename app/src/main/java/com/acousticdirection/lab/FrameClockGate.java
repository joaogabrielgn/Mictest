package com.acousticdirection.lab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/**
 * ARCore says Frame.getTimestamp() timebase isn't formally defined.
 * Never blindly align to audio BOOTTIME. Accept only capture timestamps
 * that track elapsedRealtimeNanos with plausible frame age and drift.
 * A plausible age is necessary, not proof of common clock hardware.
 */
public final class FrameClockGate {
    private final ArrayDeque<Long> agesNs=new ArrayDeque<>();
    private long lastTimestamp=-1;
    private long previousAge=-1;
    private boolean compatible=false;
    private String status="AQUECENDO_RELOGIOS";
    private double medianAgeMs=Double.NaN;
    public synchronized boolean observe(long capture,long processed){
        long age=processed-capture;
        if(capture<=0||processed<=0||age<0||age>900_000_000L||
           (lastTimestamp>0&&capture<lastTimestamp)){
            compatible=false;status="RELOGIOS_INCOMPATIVEIS";
            agesNs.clear();previousAge=-1;lastTimestamp=-1;
            return false;
        }
        if(capture==lastTimestamp)return compatible;
        lastTimestamp=capture;
        agesNs.addLast(age);
        if(agesNs.size()>50)agesNs.removeFirst();
        ArrayList<Long> sorted=new ArrayList<>(agesNs);
        Collections.sort(sorted);
        long med=sorted.get(sorted.size()/2);
        medianAgeMs=med/1e6;
        int deviations=0;
        for(long t:sorted)if(Math.abs(t-med)>180_000_000L)deviations++;
        compatible=sorted.size()>=5 && deviations<=sorted.size()/3;
        status=compatible?"COMPATIVEL_PROVISORIO":
            sorted.size()<5?"AQUECENDO_RELOGIOS":"LATENCIA_CAMERA_INSTAVEL";
        return compatible;
    }
    public synchronized boolean compatible(){return compatible;}
    public synchronized String status(){return status;}
    public synchronized double medianFrameAgeMs(){return medianAgeMs;}
    public synchronized void reset(){
        agesNs.clear();lastTimestamp=-1;previousAge=-1;compatible=false;
        medianAgeMs=Double.NaN;status="AQUECENDO_RELOGIOS";
    }
}
