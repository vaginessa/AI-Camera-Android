package com.example.chin.instancesegmentation;

import android.os.Parcel;
import android.os.Parcelable;

public class ImageItem implements Parcelable {
    private String mTitle;
    private String mPath;

    public ImageItem(String title, String path) {
        mTitle = title;
        mPath = path;
    }

    public ImageItem(Parcel in) {
        mTitle = in.readString();
        mPath = in.readString();
    }

    public static final Creator<ImageItem> CREATOR = new Creator<ImageItem>() {
        @Override
        public ImageItem createFromParcel(Parcel in) {
            return new ImageItem(in);
        }

        @Override
        public ImageItem[] newArray(int size) {
            return new ImageItem[size];
        }
    };

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String path) {
        mPath = path;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mTitle);
        dest.writeString(mPath);
    }
}
