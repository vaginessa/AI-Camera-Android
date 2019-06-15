package com.lun.chin.aicamera;

import android.os.Parcel;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;

import com.lun.chin.aicamera.listener.OnDeleteImageListener;

import java.util.ArrayList;

public class GalleryPagerAdapter extends FragmentStatePagerAdapter {
    private ArrayList<ImageItem> images;

    public GalleryPagerAdapter(FragmentManager fm, ArrayList<ImageItem> images) {
        super(fm);
        this.images = images;
    }

    @Override
    public Fragment getItem(final int position) {
        OnDeleteImageListener onDelete = new OnDeleteImageListener() {
            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {}

            @Override
            public void onDeleteImage(ImageItem imageItem) {
                images.remove(position);
                notifyDataSetChanged();
            }
        };

        if (position >= images.size()) {
            return ImageDetailFragment.newInstance(new ImageItem("blank", "blank"), onDelete);
        } else {
            return ImageDetailFragment.newInstance(images.get(position), onDelete);
        }
    }

    @Override
    public int getCount() {
        int count = images.size();
        return count == 0 ? 1 : count;
    }

    @Override
    public int getItemPosition(Object object) {
        return PagerAdapter.POSITION_NONE;
    }
}
