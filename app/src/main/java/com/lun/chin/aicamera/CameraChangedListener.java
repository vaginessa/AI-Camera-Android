package com.lun.chin.aicamera;

import android.hardware.Camera;

public interface CameraChangedListener {
    void onCameraChangedListener(Camera camera, boolean isFrontFacing);
}
