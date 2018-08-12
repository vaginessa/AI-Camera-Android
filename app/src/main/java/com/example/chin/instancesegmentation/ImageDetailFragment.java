package com.example.chin.instancesegmentation;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.chrisbanes.photoview.PhotoView;

public class ImageDetailFragment extends Fragment {
    private static final String EXTRA_IMAGE = "image_item";
    private View mView;

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
        mView = view;
        setView();
    }

    public void updateFragment() {
        setView();
    }

    private void setView() {
        final ImageItem imageItem = getArguments().getParcelable(EXTRA_IMAGE);
        final int width = Resources.getSystem().getDisplayMetrics().widthPixels;
        final int height = Resources.getSystem().getDisplayMetrics().heightPixels;
        final Bitmap bitmap = ImageManager.getInstance().getSmallBitmap(imageItem, width, height);

        if (bitmap == null) {
            final TextView textView = mView.findViewById(R.id.loading_text);
            textView.setText(R.string.loading_text);
        } else {
            final PhotoView photoView = mView.findViewById(R.id.detail_image);
            photoView.setImageBitmap(bitmap);
        }
    }
}
