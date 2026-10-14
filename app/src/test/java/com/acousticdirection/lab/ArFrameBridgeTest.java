package com.acousticdirection.lab;

import static org.junit.Assert.*;
import org.junit.Test;

public class ArFrameBridgeTest {
    @Test public void selectsClosestPoseAndRejectsClockSkew(){
        ArFrameBridge bridge=new ArFrameBridge();
        double[] a={1,0,0},src={0,0,1},p={0,0,0},q={1,0,0,0};
        bridge.append(ArFrameBridge.create(1_000_000_000L,17,a,src,p,q,false,0,3));
        bridge.append(ArFrameBridge.create(1_040_000_000L,18,a,src,p,q,false,0,3));
        assertEquals(18,bridge.closest(1_050_000_000L).cameraFrameNs);
        assertEquals(0,bridge.closest(1_050_000_000L).expectedProjection(),.00001);
        assertNull(bridge.closest(1_300_000_000L));
    }
    @Test public void bridgeDoesNotExposeMutableReferenceArrays(){
        ArFrameBridge bridge=new ArFrameBridge();
        double[] a={1,0,0},src={1,0,0},pos={0,0,0},q={1,0,0,0};
        bridge.append(ArFrameBridge.create(1_000_000_000L,19,a,src,pos,q,false,0,2));
        a[0]=0;src[0]=0;q[0]=0;
        ArFrameBridge.Sample s=bridge.closest(1_000_000_000L);
        assertEquals(1,s.expectedProjection(),1e-9);
        assertEquals(1,s.sensorQuaternion[0],1e-9);
    }
}
