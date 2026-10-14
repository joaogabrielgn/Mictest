package com.acousticdirection.lab;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnsetInspectorTest {
    @Test public void firstRiseTracksTwoDifferentArrivalTimes(){
        short[] s=new short[2048*2];
        for(int i=0;i<24;i++){
            short value=(short)((i%2==0?1:-1)*18000);
            s[(230+i)*2]=value;
            s[(241+i)*2+1]=value;
        }
        OnsetInspector.Pair p=OnsetInspector.examine(s,2,1,48000);
        assertTrue(p.usable());
        assertEquals(11,p.onsetB-p.onset0,1);
        assertEquals(11*1.e6/48000,p.delayUs,25.0);
    }
    @Test public void silenceHasNoValidArrival(){
        OnsetInspector.Pair p=OnsetInspector.examine(new short[2048*2],2,1,48000);
        assertFalse(p.usable());
    }
}
