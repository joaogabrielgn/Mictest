package com.acousticdirection.lab;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public final class EventDetectorTest {
    @Test public void detectsClicksAcrossReadBoundaries() {
        EventDetector d = new EventDetector(2,48000);
        int n=96000;
        short[] pcm = new short[n*2];
        int[] clicks={4030,26010,48010,71050};
        for(int onset:clicks){
            for(int i=0;i<24;i++){
                short s=(short)((i%2==0?1:-1)*23000);
                pcm[(onset+i)*2]=s; pcm[(onset+i)*2+1]=s;
            }
        }
        final List<EventDetector.Event> events=new ArrayList<>();
        for(int from=0;from<n;from+=231){
            int count=Math.min(231,n-from)*2;
            short[] buffer=new short[count];
            System.arraycopy(pcm,from*2,buffer,0,count);
            d.feed(buffer,count,events::add);
        }
        assertEquals(clicks.length, events.size());
        assertEquals(clicks.length,d.detectedCount());
        for(int i=0;i<clicks.length;i++){
            EventDetector.Event e=events.get(i);
            assertTrue(Math.abs(e.onsetFrame-clicks[i])<70);
            assertEquals(2048*2,e.pcm.length);
            assertEquals(e.onsetFrame-256,e.windowStartFrame);
            assertTrue(e.triggerRatio>5);
        }
    }
    @Test public void rejectsRepeatedEchoAfterOneClick() {
        EventDetector d=new EventDetector(2,48000);
        short[] pcm=new short[48000*2];
        // First impulse + reverberant echoes at 140 ms and 280 ms.
        for(int onset : new int[]{5000,11720,18440}) {
            for(int i=0;i<32;i++){
                short v=(short)((i%2==0?1:-1)*21000);
                pcm[(onset+i)*2]=v;pcm[(onset+i)*2+1]=v;
            }
        }
        List<EventDetector.Event> found=new ArrayList<>();
        for(int from=0;from<48000;from+=317){
            int end=Math.min(317,48000-from)*2;
            short[] chunk=new short[end];
            System.arraycopy(pcm,from*2,chunk,0,end);
            d.feed(chunk,end,found::add);
        }
        assertEquals(1,found.size());
        assertTrue(d.suppressedBlocks()>0);
    }

    @Test public void steadySilenceDoesNotTrigger() {
        EventDetector d=new EventDetector(2,48000);
        short[] pcm=new short[96000];
        d.feed(pcm,pcm.length,event->fail("Silence should not trigger"));
        assertEquals(0,d.detectedCount());
    }
    @Test public void acceptsMultipleChannelConfigurations(){
        EventDetector d=new EventDetector(4,48000);
        short[] frame=new short[2048*4];
        d.feed(frame,frame.length,event->fail("No transient"));
        assertEquals(2048,d.framesConsumed());
    }
}
