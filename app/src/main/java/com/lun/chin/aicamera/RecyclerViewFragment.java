package com.lun.chin.aicamera;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.lun.chin.aicamera.listener.GalleryItemClickListener;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RecyclerViewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RecyclerViewFragment extends Fragment implements GalleryItemClickListener {
    public static final String TAG = RecyclerViewFragment.class.getSimpleName();

    private ArrayList<ImageItem> mImages;
    private RecyclerViewAdapter mAdapter;

    public RecyclerViewFragment() {
        // Required empty public constructor
    }

    public void setImages(ArrayList<ImageItem> images) {
        mImages = images;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment RecyclerViewFragment.
     */
    public static RecyclerViewFragment newInstance(ArrayList<ImageItem> images) {
        RecyclerViewFragment fragment = new RecyclerViewFragment();
        fragment.setImages(images);
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
        return inflater.inflate(R.layout.fragment_recycler_view, container, false);
    }

    @Override
    public void onGalleryItemClickListener(int position, ImageItem imageModel, ImageView imageView) {

        GalleryViewPagerFragment galleryViewPagerFragment =
                GalleryViewPagerFragment.newInstance(position, mImages);

        getFragmentManager()
                .beginTransaction()
                .addToBackStack(TAG)
                .replace(R.id.container, galleryViewPagerFragment)
                .commit();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAdapter = new RecyclerViewAdapter(getActivity(), mImages,this);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    public void notifyImageChange(String filename) {
        for (int pos = 0; pos < mImages.size(); ++pos) {
            if (mImages.get(pos).getTitle() == filename) {
                mAdapter.notifyItemChanged(pos);
                break;
            }
        }
    }
}
