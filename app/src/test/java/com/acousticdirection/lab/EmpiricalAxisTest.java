package com.acousticdirection.lab;
import org.junit.Test;
import static org.junit.Assert.*;
public class EmpiricalAxisTest {
    @Test public void knownPilotAnchorSignsMatchPhysicalMicEnds() {
        EmpiricalAxis.Axis inferior=EmpiricalAxis.estimate(-434.7);
        EmpiricalAxis.Axis upper=EmpiricalAxis.estimate(472.1);
        EmpiricalAxis.Axis center=EmpiricalAxis.estimate(-2.8);
        assertEquals(-1.0,inferior.rawScore,1e-9);
        assertEquals(+1.0,upper.rawScore,1e-9);
        assertEquals(0.0,center.rawScore,1e-9);
        assertEquals("PROX_INFERIOR",inferior.sector);
        assertEquals("PROX_SUPERIOR",upper.sector);
        assertEquals("FAIXA_CENTRAL",center.sector);
        assertTrue(inferior.beyondAirLimit);
        assertTrue(upper.beyondAirLimit);
    }
    @Test public void intermediateResultsRemainOneDimensional() {
        assertEquals(-0.5,EmpiricalAxis.estimate(-218.75).rawScore,1e-9);
        assertEquals(+0.5,EmpiricalAxis.estimate(234.65).rawScore,1e-9);
        assertFalse(EmpiricalAxis.estimate(800).inPilotRange);
        assertEquals("FORA_PERFIL_PILOTO",EmpiricalAxis.estimate(800).sector);
    }
}
