package com.example.android.google.wearable.app.data;

import android.hardware.Sensor;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.example.android.google.wearable.app.ClientPaths;


public class SensorNames {
    public SparseArray<String> names;
    public SparseIntArray serverSensors;

    public SensorNames() {
        names = new SparseArray<String>();
        serverSensors = new SparseIntArray ();

        names.append(0, "Debug Sensor");
        names.append(Sensor.TYPE_ACCELEROMETER, "Accelerometer");
        names.append(Sensor.TYPE_AMBIENT_TEMPERATURE, "Ambient temperatur");
        names.append(Sensor.TYPE_GAME_ROTATION_VECTOR, "Game Rotation Vector");
        names.append(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "Geomagnetic Rotation Vector");
        names.append(Sensor.TYPE_GRAVITY, "Gravity");
        names.append(Sensor.TYPE_GYROSCOPE, "Gyroscope");
        names.append(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, "Gyroscope (Uncalibrated)");
        names.append(Sensor.TYPE_HEART_RATE, "Heart Rate");
        names.append(Sensor.TYPE_LIGHT, "Light");
        names.append(Sensor.TYPE_LINEAR_ACCELERATION, "Linear Acceleration");
        names.append(Sensor.TYPE_MAGNETIC_FIELD, "Magnetic Field");
        names.append(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, "Magnetic Field (Uncalibrated)");
        names.append(Sensor.TYPE_PRESSURE, "Pressure");
        names.append(Sensor.TYPE_PROXIMITY, "Proximity");
        names.append(Sensor.TYPE_RELATIVE_HUMIDITY, "Relative Humidity");
        names.append(Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector");
        names.append(Sensor.TYPE_SIGNIFICANT_MOTION, "Significant Motion");
        names.append(Sensor.TYPE_STEP_COUNTER, "Step Counter");
        names.append(Sensor.TYPE_STEP_DETECTOR, "Step Detector");
        names.append(21, "Ss Heart Rate");
        names.append(ClientPaths.DUST_SENSOR_ID, "Dust Sensor");
        names.append(ClientPaths.SPIRO_SENSOR_ID, "Spirometer Sensor");


        serverSensors.append(ClientPaths.DUST_SENSOR_ID, 4);
        serverSensors.append(ClientPaths.SPIRO_SENSOR_ID, 5);

        serverSensors.append(Sensor.TYPE_HEART_RATE, 3);
        serverSensors.append(21, 3);

        serverSensors.append(Sensor.TYPE_LINEAR_ACCELERATION, 2);
    }

    public String getName(int sensorId) {
        String name = names.get(sensorId);

        if (name == null) {
            name = "Unknown";
        }

        return name;
    }

    public Integer getServerID(int sensorId) {
        Integer v = serverSensors.get(sensorId);
        return v != null ? v : -1;
    }
}
