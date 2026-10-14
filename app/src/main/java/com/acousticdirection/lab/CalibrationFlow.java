package com.acousticdirection.lab;

/**
 * UI-independent state machine. All mutations are synchronized to avoid
 * recording callbacks racing with UI actions.
 */
public final class CalibrationFlow {
    public enum State {IDLE, SOURCE_MARKED, CALIBRATING, READY_TO_FREEZE, VALIDATING, DONE}
    private State state=State.IDLE;
    private boolean capturing=false;
    private long frozenAtNs=-1;
    public synchronized State state(){return state;}
    public synchronized boolean recording(){return capturing;}
    public synchronized long frozenAtNs(){return frozenAtNs;}
    public synchronized void mark(){
        if(capturing)throw new IllegalStateException("Pare o áudio antes de trocar a fonte");
        state=State.SOURCE_MARKED;
        frozenAtNs=-1;
    }
    public synchronized void clear(){
        if(capturing)throw new IllegalStateException("Pare o áudio primeiro");
        state=State.IDLE;frozenAtNs=-1;
    }
    public synchronized void begin(){
        if(capturing)throw new IllegalStateException("Áudio já está ativo");
        if(state!=State.SOURCE_MARKED&&state!=State.CALIBRATING&&
           state!=State.READY_TO_FREEZE&&state!=State.VALIDATING)
            throw new IllegalStateException("Marque a fonte antes de iniciar");
        if(state==State.SOURCE_MARKED)state=State.CALIBRATING;
        capturing=true;
    }
    public synchronized void stop(){capturing=false;}
    public synchronized void ready(boolean acceptable){
        if(state==State.CALIBRATING&&acceptable)state=State.READY_TO_FREEZE;
        else if(state==State.READY_TO_FREEZE&&!acceptable)state=State.CALIBRATING;
    }
    public synchronized void freeze(long cutoffNs){
        if(capturing)throw new IllegalStateException("Pare o áudio antes de congelar");
        if(state!=State.READY_TO_FREEZE)throw new IllegalStateException("Ainda faltam dados de calibração");
        if(cutoffNs<=0)throw new IllegalArgumentException("Instante inválido");
        frozenAtNs=cutoffNs;state=State.VALIDATING;
    }
    public synchronized void complete(){
        if(capturing)throw new IllegalStateException("Pare o áudio antes de encerrar");
        if(state!=State.VALIDATING)throw new IllegalStateException("Validação não iniciada");
        state=State.DONE;
    }
    public synchronized boolean canCollect(long timestampNs){
        return capturing && ((state==State.CALIBRATING || state==State.READY_TO_FREEZE)
            || (state==State.VALIDATING&&timestampNs>frozenAtNs));
    }
    public synchronized String phase(){
        return state==State.VALIDATING||state==State.DONE?"VALID":"TRAIN";
    }
}
