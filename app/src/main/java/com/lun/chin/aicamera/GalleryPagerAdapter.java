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
        final ImageItem image = images.get(position);
        return ImageDetailFragment.newInstance(image, new OnDeleteImageListener() {
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
        });
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public int getItemPosition(Object object) {
        return PagerAdapter.POSITION_NONE;
    }
}
