package com.lun.chin.aicamera.listener;

import android.hardware.Camera;

public interface CameraChangedListener {
    void onCameraChangedListener(Camera camera, boolean isFrontFacing);
}
