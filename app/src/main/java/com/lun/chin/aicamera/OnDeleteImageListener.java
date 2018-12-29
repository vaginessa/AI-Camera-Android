package com.lun.chin.aicamera;

import android.os.Parcelable;

public interface OnDeleteImageListener extends Parcelable {
    void onDeleteImage(ImageItem imageItem);
}
