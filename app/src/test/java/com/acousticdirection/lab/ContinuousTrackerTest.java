package com.acousticdirection.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContinuousTrackerTest {
    private static final int RATE=48000;
    private short[] syntheticStereo(int frames,int delay,boolean tonal){
        Random random=new Random(3301);
        double[] source=new double[frames];
        for(int i=0;i<frames;i++)
            source[i]=tonal ? .13*Math.sin(2*Math.PI*1700*i/RATE) :
                .10*random.nextGaussian();
        short[] pcm=new short[frames*2];
        for(int i=0;i<frames;i++){
            double a=source[i],b=i-delay>=0&&i-delay<frames?source[i-delay]:0;
            pcm[2*i]=(short)Math.round(Math.max(-.98,Math.min(.98,a))*32767);
            pcm[2*i+1]=(short)Math.round(Math.max(-.98,Math.min(.98,b))*32767);
        }
        return pcm;
    }
    private List<ContinuousTracker.Window> capture(short[] stereo){
        ContinuousTracker tracker=new ContinuousTracker(2,RATE);
        ArrayList<ContinuousTracker.Window> windows=new ArrayList<>();
        int frames=stereo.length/2;
        for(int from=0;from<frames;from+=607) {
            int chunk=Math.min(607,frames-from)*2;
            short[] read=new short[chunk];
            System.arraycopy(stereo,from*2,read,0,chunk);
            tracker.feed(read,chunk,windows::add);
        }
        assertEquals(frames,tracker.totalFrames());
        assertEquals(1+(frames-ContinuousTracker.WINDOW)/ContinuousTracker.HOP,windows.size());
        return windows;
    }
    @Test public void handlesContinuousBroadbandNoiseWithPositiveDelay(){
        List<ContinuousTracker.Window> windows=capture(syntheticStereo(48000,9,false));
        int valid=0;
        for(ContinuousTracker.Window w:windows)
            if(w.reliable){valid++;assertEquals(9.0*1.e6/RATE,w.rawDelayUs,37.0);}
        assertTrue("Multiband broadband delay should be observable",valid>=20);
        ContinuousTracker.Window last=windows.get(windows.size()-1);
        assertTrue(last.reliable);
        assertEquals(9.0*1.e6/RATE,last.smoothedDelayUs,36.0);
    }
    @Test public void negativeDelayIsNotMistakenForPositive(){
        List<ContinuousTracker.Window> windows=capture(syntheticStereo(40000,-12,false));
        int valid=0;
        for(ContinuousTracker.Window w:windows)if(w.reliable){
            valid++;assertTrue(w.rawDelayUs < -200);
        }
        assertTrue(valid>10);
    }
    @Test public void silenceDoesNotHoldStalePosition(){
        short[] zero=new short[48000*2];
        List<ContinuousTracker.Window> w=capture(zero);
        assertEquals("SILENCIO",w.get(w.size()-1).status);
        assertFalse(w.get(w.size()-1).reliable);
        assertTrue(Double.isNaN(w.get(w.size()-1).smoothedDelayUs));
    }
    @Test public void narrowToneIsNotClassifiedAsRobustMultiband(){
        List<ContinuousTracker.Window> w=capture(syntheticStereo(48000,7,true));
        int reliable=0;
        for(ContinuousTracker.Window part:w)if(part.reliable)reliable++;
        assertEquals(0,reliable);
    }
    @Test public void callbacksIncludeFrameCenteredTimestamps(){
        List<ContinuousTracker.Window> w=capture(syntheticStereo(16000,4,false));
        assertEquals(ContinuousTracker.WINDOW/2,w.get(0).centerFrame);
        assertEquals(ContinuousTracker.WINDOW/2+ContinuousTracker.HOP,w.get(1).centerFrame);
    }
}
