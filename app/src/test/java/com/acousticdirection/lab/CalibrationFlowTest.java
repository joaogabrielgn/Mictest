package com.acousticdirection.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class CalibrationFlowTest {
    @Test public void separatesIndependentValidationFromTraining(){
        CalibrationFlow f=new CalibrationFlow();
        assertEquals(CalibrationFlow.State.IDLE,f.state());
        f.mark();f.begin();
        assertEquals("TRAIN",f.phase());
        assertTrue(f.canCollect(1_000_000_000L));
        f.ready(true);assertEquals(CalibrationFlow.State.READY_TO_FREEZE,f.state());
        try{f.freeze(2_000_000_000L);fail("must stop before freezing");}
        catch(IllegalStateException expected){}
        f.stop();f.freeze(2_000_000_000L);
        assertEquals(CalibrationFlow.State.VALIDATING,f.state());
        f.begin();assertFalse(f.canCollect(2_000_000_000L));
        assertTrue(f.canCollect(2_000_000_001L));
        assertEquals("VALID",f.phase());
        f.stop();f.complete();
        assertEquals(CalibrationFlow.State.DONE,f.state());
        assertFalse(f.canCollect(9_000_000_000L));
    }
    @Test public void noFreezeWithoutGoodProfile(){
        CalibrationFlow f=new CalibrationFlow();
        f.mark();f.begin();f.stop();
        try{f.freeze(10);fail("must require ready");}
        catch(IllegalStateException expected){}
        f.begin();f.ready(true);f.ready(false);
        f.stop();assertEquals(CalibrationFlow.State.CALIBRATING,f.state());
    }
    @Test public void resettingRequiresStoppedAudio(){
        CalibrationFlow f=new CalibrationFlow();f.mark();f.begin();
        try{f.clear();fail("cannot clear while recording");}
        catch(IllegalStateException expected){}
        f.stop();f.clear();assertEquals(CalibrationFlow.State.IDLE,f.state());
    }
}
