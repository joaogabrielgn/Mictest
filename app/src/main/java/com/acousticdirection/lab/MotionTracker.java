package com.acousticdirection.lab;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Log raw sensor events for offline calibration. These are NOT yet fused with audio.
 * SensorEvent.timestamp and SystemClock.elapsedRealtimeNanos use the BOOTTIME base.
 */
public final class MotionTracker implements SensorEventListener {
    private final SensorManager sensors;
    private final PoseHistory history = new PoseHistory();
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private final Sensor rotation, gyro, accelerometer;
    private volatile Snapshot latest = Snapshot.EMPTY;
    private volatile boolean enabled = false;
    private volatile float gx,gy,gz;
    private volatile long lastGyroNs;
    private volatile String rotationLabel;

    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0, 0);
        public final long eventNs;
        public final double yaw, pitch, roll, gx, gy, gz;
        Snapshot(long eventNs, double yaw, double pitch, double roll,
                 double gx, double gy, double gz) {
            this.eventNs=eventNs; this.yaw=yaw; this.pitch=pitch; this.roll=roll;
            this.gx=gx; this.gy=gy; this.gz=gz;
        }
    }

    public MotionTracker(Context context) {
        sensors=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        Sensor r=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (r == null) r=sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        rotation=r;
        gyro=sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometer=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationLabel=rotation==null?"NO_ROTATION_SENSOR":rotation.getName();
    }
    public String inventory() {
        return String.format(Locale.US,"IMU: rotation=%s; gyro=%s; accelerometer=%s",
            rotationLabel, gyro==null?"NONE":gyro.getName(),
            accelerometer==null?"NONE":accelerometer.getName());
    }
    public synchronized void start() {
        lines.clear();
        history.clear();
        latest=Snapshot.EMPTY;
        enabled=true;
        if(rotation!=null)sensors.registerListener(this,rotation,SensorManager.SENSOR_DELAY_GAME);
        if(gyro!=null)sensors.registerListener(this,gyro,SensorManager.SENSOR_DELAY_GAME);
        if(accelerometer!=null)sensors.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_GAME);
    }
    public synchronized void stop() {
        enabled=false;
        sensors.unregisterListener(this);
    }
    public Snapshot snapshot() {return latest;}
    public PoseHistory.Pose poseAt(long timeNs) {return history.interpolate(timeNs);}
    public String pollLine() { return lines.poll(); }
    public void clearLines(){lines.clear();}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}
    @Override public void onSensorChanged(SensorEvent e) {
        if(!enabled)return;
        switch(e.sensor.getType()){
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GAME_ROTATION_VECTOR: {
                float[] rotationMatrix=new float[9], angles=new float[3], quaternion=new float[4];
                SensorManager.getRotationMatrixFromVector(rotationMatrix,e.values);
                SensorManager.getOrientation(rotationMatrix,angles);
                SensorManager.getQuaternionFromVector(quaternion,e.values);
                history.add(e.timestamp, quaternion[0],quaternion[1],quaternion[2],quaternion[3]);
                latest=new Snapshot(e.timestamp,Math.toDegrees(angles[0]),
                    Math.toDegrees(angles[1]),Math.toDegrees(angles[2]),gx,gy,gz);
                lines.add(String.format(Locale.US,"%d,ROT,%.8f,%.8f,%.8f,%.8f",
                    e.timestamp,quaternion[0],quaternion[1],quaternion[2],quaternion[3]));
                break;
            }
            case Sensor.TYPE_GYROSCOPE:
                gx=e.values[0];gy=e.values[1];gz=e.values[2];lastGyroNs=e.timestamp;
                lines.add(String.format(Locale.US,"%d,GYRO,%.8f,%.8f,%.8f,",
                    e.timestamp,gx,gy,gz));
                break;
            case Sensor.TYPE_ACCELEROMETER:
                lines.add(String.format(Locale.US,"%d,ACCEL,%.8f,%.8f,%.8f,",
                    e.timestamp,e.values[0],e.values[1],e.values[2]));
                break;
            default: break;
        }
    }
}
