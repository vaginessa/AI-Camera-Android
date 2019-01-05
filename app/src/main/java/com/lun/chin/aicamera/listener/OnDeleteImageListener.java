package com.lun.chin.aicamera.listener;

import android.os.Parcelable;

import com.lun.chin.aicamera.ImageItem;

public interface OnDeleteImageListener extends Parcelable {
    void onDeleteImage(ImageItem imageItem);
}
