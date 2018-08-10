package com.example.chin.instancesegmentation;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import java.util.ArrayList;

public class GalleryPagerAdapter extends FragmentStatePagerAdapter {
    private ArrayList<ImageItem> images;

    public GalleryPagerAdapter(FragmentManager fm, ArrayList<ImageItem> images) {
        super(fm);
        this.images = images;
    }

    @Override
    public Fragment getItem(int position) {
        ImageItem image = images.get(position);
        return ImageDetailFragment.newInstance(image);
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public int getItemPosition(Object object) {
        ImageDetailFragment f = (ImageDetailFragment)object;
        f.updateFragment();
        return super.getItemPosition(object);
    }
}
