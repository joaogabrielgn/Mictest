package com.acousticdirection.lab;

import java.util.Locale;

/**
 * Device-and-protocol-specific PILOT calibration, NOT a geometric 3D model.
 * Labels refer to PHYSICAL microphone ends (inferior / superior), not screen sides.
 * Anchors are offline exploratory medians from user-labelled Galaxy S25 experiments
 * of 2026-10-14 with CAMCORDER stereo / 48 kHz / short broadband clinks.
 */
public final class EmpiricalAxis {
    public static final double INFERIOR_US = -434.7;
    public static final double CENTER_US = -2.8;
    public static final double SUPERIOR_US = +472.1;
    public static final String PROFILE = "S25_PILOTO_20261014";
    /** Geometric maximum is only a diagnostic for propagation by air. */
    public static final double THEORETICAL_AIR_LIMIT_US = 0.1426 / 343.0 * 1.0e6;

    public static final class Axis {
        public final double rawScore;
        public final String sector;
        public final boolean inPilotRange;
        public final boolean beyondAirLimit;
        Axis(double score,boolean inRange,boolean beyond){
            rawScore=score;inPilotRange=inRange;beyondAirLimit=beyond;
            if(!inRange) sector="FORA_PERFIL_PILOTO";
            else if(score<=-0.4) sector="PROX_INFERIOR";
            else if(score>=0.4) sector="PROX_SUPERIOR";
            else sector="FAIXA_CENTRAL";
        }
        public String display(){
            return String.format(Locale.US,
                "Índice eixo %.2f (%s) | %s",
                rawScore,sector,beyondAirLimit?"excede limite aéreo ideal":"dentro limite aéreo ideal");
        }
    }
    private EmpiricalAxis(){}
    /**
     * Piecewise-linear, empirically labelled 1D score. NOT sine(angle).
     * Output is deliberately NOT clamped: out-of-range observations remain visible.
     */
    public static Axis estimate(double delayUs){
        double s;
        if(delayUs <= CENTER_US) s=(delayUs-CENTER_US)/(CENTER_US-INFERIOR_US);
        else s=(delayUs-CENTER_US)/(SUPERIOR_US-CENTER_US);
        return new Axis(s,Double.isFinite(delayUs) &&
            delayUs>=INFERIOR_US && delayUs<=SUPERIOR_US,
            Math.abs(delayUs)>THEORETICAL_AIR_LIMIT_US);
    }
}
