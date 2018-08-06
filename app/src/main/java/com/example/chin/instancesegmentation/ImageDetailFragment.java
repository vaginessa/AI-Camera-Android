package com.example.chin.instancesegmentation;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.chrisbanes.photoview.PhotoView;

public class ImageDetailFragment extends Fragment {
    private static final String EXTRA_IMAGE = "image_item";

    public ImageDetailFragment() { }

    public static ImageDetailFragment newInstance(ImageItem imageItem) {
        ImageDetailFragment fragment = new ImageDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_IMAGE, imageItem);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image_detail, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final ImageItem imageItem = getArguments().getParcelable(EXTRA_IMAGE);
        final PhotoView photoView = (PhotoView)view.findViewById(R.id.detail_image);

        photoView.setImageBitmap(BitmapFactory.decodeFile(imageItem.getPath()));
    }
}
