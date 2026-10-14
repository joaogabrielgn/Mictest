package com.acousticdirection.lab;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.view.Surface;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * ARCore owns the back camera and its GPU passthrough stream. No simultaneous
 * CameraX capture. All Session.update(), marker clicks and anchors happen
 * on GL thread; immutable samples are handed to the audio worker.
 */
public final class ArCameraRenderer implements GLSurfaceView.Renderer {
    public interface Listener {
        void onVisual(int x,int y,boolean visible,String referenceMode,
                      String tracking,String description);
        void onMark(String referenceMode,float xPx,float yPx,int viewWidth,int viewHeight,
                    float[] projection);
        void onError(String error);
    }
    private final Listener listener;
    private final ArFrameBridge poseBridge;
    private final FrameClockGate clockGate;
    private volatile Session session;
    private int texture=-1,program=-1,attrPos,attrUv,textureUniform;
    private int width=1,height=1;
    private boolean changed=true,textureSet=false;
    private float tapX=-1,tapY=-1;
    private final float[] quad={-1,-1,1,-1,-1,1,1,1};
    private final float[] uv=new float[8];
    private final FloatBuffer positions=buffer(quad);
    private final FloatBuffer texCoords=buffer(uv);
    private Anchor sourceAnchor;
    private double[] fixedBearing,markPosition;
    private String mode="SEM_MARCA";
    private volatile long lastUi=0;

    public ArCameraRenderer(ArFrameBridge bridge,FrameClockGate clockGate,Listener listener){
        this.listener=listener;this.poseBridge=bridge;this.clockGate=clockGate;
    }
    public void setSession(Session session){
        this.session=session;textureSet=false;changed=true;
        previousQ=null;previousNs=0;clockGate.reset();poseBridge.clear();
    }
    public synchronized void markAt(float x,float y){tapX=x;tapY=y;}
    public synchronized void clearMark(){
        if(sourceAnchor!=null){sourceAnchor.detach();sourceAnchor=null;}
        fixedBearing=null;markPosition=null;mode="SEM_MARCA";
        poseBridge.clear();
    }
    public synchronized boolean hasMark(){return fixedBearing!=null||sourceAnchor!=null;}
    public synchronized String referenceMode(){return mode;}
    private static FloatBuffer buffer(float[] data){
        FloatBuffer b=ByteBuffer.allocateDirect(data.length*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(data);b.position(0);return b;
    }
    private static int shader(int type,String src){
        int shader=GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader,src);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException("GL shader: "+GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
    @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                                          javax.microedition.khronos.egl.EGLConfig config){
        int[] id=new int[1];GLES20.glGenTextures(1,id,0);texture=id[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        String vertex="attribute vec2 aPosition;attribute vec2 aUv;varying vec2 vUv;"+
            "void main(){gl_Position=vec4(aPosition,0.0,1.0);vUv=aUv;}";
        String fragment="#extension GL_OES_EGL_image_external : require\n"+
            "precision mediump float;uniform samplerExternalOES cameraTex;varying vec2 vUv;"+
            "void main(){gl_FragColor=texture2D(cameraTex,vUv);}";
        int vs=shader(GLES20.GL_VERTEX_SHADER,vertex),fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
        program=GLES20.glCreateProgram();
        GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);
        GLES20.glLinkProgram(program);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException("GL program: "+GLES20.glGetProgramInfoLog(program));
        attrPos=GLES20.glGetAttribLocation(program,"aPosition");
        attrUv=GLES20.glGetAttribLocation(program,"aUv");
        textureUniform=GLES20.glGetUniformLocation(program,"cameraTex");
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
        textureSet=false;changed=true;
    }
    @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){
        width=w;height=h;changed=true;
        GLES20.glViewport(0,0,w,h);
    }
    @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
        GLES20.glClearColor(.03f,.07f,.10f,1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Session s=session;
        if(s==null||texture<0)return;
        try{
            if(!textureSet){s.setCameraTextureName(texture);textureSet=true;}
            if(changed){
                int rotation=android.view.Surface.ROTATION_0;
                rotation=displayRotation;
                s.setDisplayGeometry(rotation,width,height);
                changed=false;
            }
            Frame frame=s.update();
            if(frame.hasDisplayGeometryChanged()){
                frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    quad,Coordinates2d.TEXTURE_NORMALIZED,uv);
                texCoords.position(0);texCoords.put(uv).position(0);
            }
            drawBackground();
            Camera camera=frame.getCamera();
            if(camera.getTrackingState()!=TrackingState.TRACKING){
                showStatus(-1,-1,false,"ARCore: sem rastreamento","Mova devagar em ambiente iluminado");
                return;
            }
            float x,y;
            synchronized(this){x=tapX;y=tapY;tapX=tapY=-1;}
            if(x>=0 && y>=0)mark(frame,camera,x,y);
            if(!hasMark()){
                showStatus(-1,-1,false,"ARCore rastreando","Toque na fonte sonora");
                return;
            }
            Pose cameraPose=camera.getDisplayOrientedPose();
            Pose sensorPose=frame.getAndroidSensorPose();
            double[] cameraPosition=translation(cameraPose);
            double[] sourceWorld;
            double trans=markPosition==null?0:ArFrameBridge.distance(cameraPosition,markPosition);
            synchronized(this){
                if(sourceAnchor!=null && sourceAnchor.getTrackingState()==TrackingState.TRACKING){
                    double[] point=translation(sourceAnchor.getPose());
                    sourceWorld=ArFrameBridge.unit(new double[]{
                        point[0]-cameraPosition[0],point[1]-cameraPosition[1],
                        point[2]-cameraPosition[2]});
                }else if(fixedBearing!=null){
                    sourceWorld=fixedBearing.clone();
                }else return;
            }
            double[] q={sensorPose.qw(),sensorPose.qx(),sensorPose.qy(),sensorPose.qz()};
            double[] axis=WorldDirectionSolver.rotate(q,WorldDirectionSolver.microphoneAxisDevice());
            long processed=SystemClock.elapsedRealtimeNanos();
            long exposure=frame.getTimestamp();
            boolean compatible=clockGate.observe(exposure,processed);
            double speed=0;
            if(previousQ!=null&&previousNs>0){
                double elapsed=(exposure-previousNs)/1e9;
                if(elapsed>.004&&elapsed<.3)
                    speed=MotionEvidence.angleDegrees(previousQ,q)/elapsed;
            }
            previousQ=q;previousNs=exposure;
            if(compatible)poseBridge.append(ArFrameBridge.createWithTiming(
                exposure,processed,axis,sourceWorld,cameraPosition,q,
                sourceAnchor!=null,trans,speed));
            double[] position;
            if(sourceAnchor!=null&&sourceAnchor.getTrackingState()==TrackingState.TRACKING)
                position=translation(sourceAnchor.getPose());
            else position=new double[]{
                cameraPosition[0]+sourceWorld[0]*4,
                cameraPosition[1]+sourceWorld[1]*4,
                cameraPosition[2]+sourceWorld[2]*4};
            float[] projected=project(camera,position);
            boolean visible=projected[2]>0 && projected[0]>=0 && projected[0]<=width &&
                projected[1]>=0 && projected[1]<=height;
            showStatus((int)projected[0],(int)projected[1],visible,
                "ARCore TRACKING",!compatible?
                "Relógio da câmera: "+clockGate.status() :
                String.format(java.util.Locale.US,"Quadro %.0f ms | %s",
                    clockGate.medianFrameAgeMs(),
                    sourceAnchor!=null?"Âncora estável":
                    trans>.15?"Mantenha o celular no mesmo lugar":"Direção marcada"));
        }catch(Exception e){
            showStatus(-1,-1,false,"Falha de frame ARCore",e.getClass().getSimpleName());
        }
    }
    private volatile int displayRotation=Surface.ROTATION_0;
    private double[] previousQ;
    private long previousNs;
    public void setDisplayRotation(int rotation){
        if(displayRotation!=rotation){displayRotation=rotation;changed=true;}
    }
    private void drawBackground(){
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glUniform1i(textureUniform,0);
        positions.position(0);texCoords.position(0);
        GLES20.glEnableVertexAttribArray(attrPos);
        GLES20.glEnableVertexAttribArray(attrUv);
        GLES20.glVertexAttribPointer(attrPos,2,GLES20.GL_FLOAT,false,0,positions);
        GLES20.glVertexAttribPointer(attrUv,2,GLES20.GL_FLOAT,false,0,texCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(attrPos);
        GLES20.glDisableVertexAttribArray(attrUv);
    }
    private void mark(Frame frame,Camera camera,float x,float y){
        synchronized(this){
            if(sourceAnchor!=null){sourceAnchor.detach();sourceAnchor=null;}
            fixedBearing=null;mode="SEM_MARCA";
            markPosition=translation(camera.getDisplayOrientedPose());
            List<HitResult> hits=frame.hitTest(x,y);
            for(HitResult hit:hits){
                double[] point=translation(hit.getHitPose());
                double distance=ArFrameBridge.distance(point,markPosition);
                if(distance<.3||distance>8.0)continue;
                if(hit.getTrackable() instanceof DepthPoint ||
                    hit.getTrackable() instanceof Point ||
                    hit.getTrackable() instanceof Plane){
                    sourceAnchor=hit.createAnchor();
                    mode="AR_ANCHOR";
                    break;
                }
            }
            if(sourceAnchor==null){
                float[] projection=new float[16],inv=new float[16];
                camera.getProjectionMatrix(projection,0,.1f,100f);
                if(!Matrix.invertM(inv,0,projection,0))return;
                float[] clip={x/Math.max(1,width)*2-1,
                    1-y/Math.max(1,height)*2,-1,1};
                float[] ray=new float[4];
                Matrix.multiplyMV(ray,0,inv,0,clip,0);
                if(Math.abs(ray[3])>1e-6f)
                    for(int i=0;i<3;i++)ray[i]/=ray[3];
                float[] cameraRay={ray[0],ray[1],ray[2]};
                float[] worldRay=camera.getDisplayOrientedPose().rotateVector(cameraRay);
                fixedBearing=ArFrameBridge.unit(new double[]{
                    worldRay[0],worldRay[1],worldRay[2]});
                mode="DIRECAO_APENAS";
            }
            poseBridge.clear();
        }
        float[] cameraProjection=new float[16];
        camera.getProjectionMatrix(cameraProjection,0,.1f,100f);
        listener.onMark(mode,x,y,width,height,cameraProjection);
    }
    private float[] project(Camera camera,double[] point){
        float[] view=new float[16],projection=new float[16];
        camera.getViewMatrix(view,0);
        camera.getProjectionMatrix(projection,0,.1f,100f);
        float[] viewPos=new float[4],out=new float[4];
        float[] p={(float)point[0],(float)point[1],(float)point[2],1};
        Matrix.multiplyMV(viewPos,0,view,0,p,0);
        Matrix.multiplyMV(out,0,projection,0,viewPos,0);
        float depth=-viewPos[2];
        if(Math.abs(out[3])<1e-7f)return new float[]{-1,-1,0};
        return new float[]{
            (out[0]/out[3]+1)*width*.5f,
            (1-out[1]/out[3])*height*.5f,depth};
    }
    private void showStatus(int x,int y,boolean visible,String tracking,String description){
        long now=SystemClock.elapsedRealtimeNanos();
        if(now-lastUi<100_000_000L)return;
        lastUi=now;listener.onVisual(x,y,visible,mode,tracking,description);
    }
    private static double[] translation(Pose pose){
        return new double[]{pose.tx(),pose.ty(),pose.tz()};
    }
}
