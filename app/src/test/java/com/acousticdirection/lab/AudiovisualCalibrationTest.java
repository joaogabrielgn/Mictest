package com.acousticdirection.lab;

import static org.junit.Assert.*;
import org.junit.Test;

public class AudiovisualCalibrationTest {
    @Test public void fitsKnownStereoOffsetAndGainAndTestsNewMotion(){
        AudiovisualCalibration fit=new AudiovisualCalibration();
        long start=1_000_000_000L;
        for(int i=0;i<55;i++){
            double p=-.9+1.8*i/54.0;
            double delay=18+453*p+(i%4-2)*1.7;
            assertTrue(fit.add(start+i*200_000_000L,p,delay,.85));
        }
        AudiovisualCalibration.Fit trained=fit.freeze();
        assertTrue(trained.display(),trained.valid);
        assertEquals(18,trained.offsetUs,4.0);
        assertEquals(453,trained.gainUs,5);
        assertTrue(fit.isValidation());
        for(int i=0;i<32;i++){
            double p=.9-1.8*i/31;
            double delay=18+453*p+(i%5-2)*2;
            assertTrue(fit.add(start+14_000_000_000L+i*200_000_000L,p,delay,.85));
        }
        AudiovisualCalibration.Validation valid=fit.validate();
        assertEquals("VALIDACAO_TDOA_OK",valid.status);
        assertTrue(valid.maeUs<10);
        assertTrue(valid.baselineMaeUs>150);
    }
    @Test public void cannotCalibrateWithOnlyOneCameraDirection(){
        AudiovisualCalibration fit=new AudiovisualCalibration();
        for(int i=0;i<60;i++)
            fit.add(i*200_000_000L+1_000_000_000L,.3,
                18+453*.3,.95);
        assertFalse(fit.freeze().valid);
        assertFalse(fit.isValidation());
    }
    @Test public void independentValidationRejectsNewElectronicOffset(){
        AudiovisualCalibration fit=new AudiovisualCalibration();
        for(int i=0;i<55;i++){
            double p=-.9+1.8*i/54;
            fit.add(i*190_000_000L+1_000_000_000L,p,20+452*p,.9);
        }
        assertTrue(fit.freeze().valid);
        for(int i=0;i<35;i++){
            double p=-.9+1.8*i/34;
            fit.add(15_000_000_000L+i*190_000_000L,p,230+452*p,.9);
        }
        assertEquals("ERRO_TDOA_ELEVADO",fit.validate().status);
    }
    @Test public void invalidOrPoorAudioCannotTeachProfile(){
        AudiovisualCalibration fit=new AudiovisualCalibration();
        assertFalse(fit.add(1_000_000_000L,.8,200,.1));
        assertFalse(fit.add(1_000_000_000L,Double.NaN,200,.8));
        assertFalse(fit.add(1_000_000_000L,.9,900,.8));
        assertEquals(0,fit.trainSize());
    }
}
