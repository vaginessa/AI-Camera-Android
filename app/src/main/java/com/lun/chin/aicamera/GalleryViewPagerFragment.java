package com.lun.chin.aicamera;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class GalleryViewPagerFragment extends Fragment {

    private static final String EXTRA_INITIAL_POS = "initial_pos";
    private static final String EXTRA_IMAGES = "images";

    private GalleryPagerAdapter mAdapter;
    private ViewPager mViewPager;
    private int mCurrentPos;

    public GalleryViewPagerFragment() {
    }

    public static GalleryViewPagerFragment newInstance(int current, ArrayList<ImageItem> images) {
        GalleryViewPagerFragment fragment = new GalleryViewPagerFragment();
        Bundle args = new Bundle();
        args.putInt(EXTRA_INITIAL_POS, current);
        args.putParcelableArrayList(EXTRA_IMAGES, images);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_gallery_view_pager, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int currentItem = getArguments().getInt(EXTRA_INITIAL_POS);
        ArrayList<ImageItem> images = getArguments().getParcelableArrayList(EXTRA_IMAGES);
        mAdapter = new GalleryPagerAdapter(getChildFragmentManager(), images);
        mViewPager = (ViewPager) view.findViewById(R.id.view_pager);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(currentItem);
    }

    @Override
    public void onPause() {
        super.onPause();
        mCurrentPos = mViewPager.getCurrentItem();
    }

    @Override
    public void onResume() {
        super.onResume();

        ArrayList<ImageItem> images = getArguments().getParcelableArrayList(EXTRA_IMAGES);
        mAdapter = new GalleryPagerAdapter(getChildFragmentManager(), images);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(mCurrentPos);
    }

    public void notifyImageChange(String filename) {
        mAdapter.notifyDataSetChanged();
    }
}
