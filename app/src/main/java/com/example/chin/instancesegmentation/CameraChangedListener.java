package com.example.chin.instancesegmentation;

import android.hardware.Camera;

public interface CameraChangedListener {
    void onCameraChangedListener(Camera camera, boolean isFrontFacing);
}
