package com.acousticdirection.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class PairQualityTest {
    @Test public void strongBroadbandCanPassAndWeakAmbiguousIsRejected(){
        PairQuality.Verdict good=PairQuality.evaluate(.9,6.0,.32,-21,false,
            12,3,.02,true,true,false);
        assertTrue(good.reason,good.accepted);
        assertTrue(good.score>.7);
        PairQuality.Verdict alias=PairQuality.evaluate(.9,6.0,.98,-21,false,
            12,3,0,true,true,false);
        assertFalse(alias.accepted);
        PairQuality.Verdict weak=PairQuality.evaluate(.9,6.0,.32,-21,false,
            12,3,0,true,false,false);
        assertEquals("RELOGIOS_NAO_VERIFICADOS",weak.reason);
    }
    @Test public void interpolatorMustBracketCloserThan70ms(){
        assertFalse(PairQuality.evaluate(.9,6,.2,-20,false,75,2,0,
            true,true,false).accepted);
        assertFalse(PairQuality.evaluate(.9,6,.2,-20,false,9,70,0,
            true,true,false).accepted);
    }
}
