package com.example.chin.instancesegmentation;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
        setViewAsync();

        final ImageItem imageItem = getArguments().getParcelable(EXTRA_IMAGE);
        ImageButton button = view.findViewById(R.id.goto_edit);
        if (ImageManager.getInstance().hasMaskData(imageItem)) {

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EditImageFragment fragment = EditImageFragment.newInstance(imageItem);

                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container, fragment)
                            .addToBackStack(null)
                            .commit();
                }
            });
        } else {
            button.setVisibility(View.INVISIBLE);
        }
    }

    public void updateFragment() {
        setViewAsync();
    }

    private void setViewAsync() {
        final ImageItem imageItem = getArguments().getParcelable(EXTRA_IMAGE);
        final int width = Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        final int height = Resources.getSystem().getDisplayMetrics().heightPixels / 2;
        final TextView textView = mView.findViewById(R.id.loading_text);
        final PhotoView photoView = mView.findViewById(R.id.detail_image);

        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                Bitmap bitmap = (Bitmap)message.obj;
                if (bitmap == null) {
                    textView.setText(R.string.loading_text);
                } else {
                    textView.setText("");
                    photoView.setImageBitmap(bitmap);
                }
            }
        };

        Thread thread = new Thread() {
            @Override
            public void run() {
                final Bitmap bitmap =
                        ImageManager.getInstance().getSmallBitmap(imageItem, width, height);
                Message message = handler.obtainMessage(1, bitmap);
                handler.sendMessage(message);
            }
        };

        thread.start();
    }
}
