package com.example.chin.instancesegmentation;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.github.chrisbanes.photoview.PhotoView;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link EditImageFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class EditImageFragment extends Fragment {
    private static final String EXTRA_IMAGE = "image_item";

    private ImageItem mImageItem;

    public EditImageFragment() {}

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param imageItem
     * @return A new instance of fragment EditImageFragment.
     */
    public static EditImageFragment newInstance(ImageItem imageItem) {
        EditImageFragment fragment = new EditImageFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_IMAGE, imageItem);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mImageItem = getArguments().getParcelable(EXTRA_IMAGE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_edit_image, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setImage(view);

        ImageButton button = view.findViewById(R.id.bnw);
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private void setImage(View view) {
        final int width = Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        final int height = Resources.getSystem().getDisplayMetrics().heightPixels / 2;
        final PhotoView photoView = view.findViewById(R.id.edit_image);

        final Bitmap bitmap = ImageManager.getInstance().getSmallBitmap(mImageItem, width, height);
        photoView.setImageBitmap(bitmap);
    }
}
