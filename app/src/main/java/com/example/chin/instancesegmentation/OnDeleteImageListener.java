package com.example.chin.instancesegmentation;

import android.os.Parcelable;

public interface OnDeleteImageListener extends Parcelable {
    void onDeleteImage(ImageItem imageItem);
}
