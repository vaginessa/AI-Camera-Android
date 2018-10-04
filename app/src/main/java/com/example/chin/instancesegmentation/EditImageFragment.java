package com.example.chin.instancesegmentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;

import com.example.chin.instancesegmentation.env.ImageUtils;
import com.github.chrisbanes.photoview.PhotoView;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link EditImageFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class EditImageFragment extends Fragment {
    private static final String EXTRA_IMAGE = "image_item";

    private ImageItem mImageItem;
    private ImageData mImageData;
    private Bitmap mProcessedBitmap;
    private PhotoView mPhotoView;
    private ImageButton mSaveButton;
    private RunInBackgroundListener mBackgroundRunner;

    private final int MAX_BLUR = 16;

    // Current settings.
    private int mBlurAmount;
    private boolean mIsGrayscale;

    private final SeekBar.OnSeekBarChangeListener mSeekBarChangedListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                // Blur amount must be an odd number.
                mBlurAmount = 2 * progress + 1;
                processImage(mBlurAmount, mIsGrayscale);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

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
            mImageData = ImageManager.getInstance().getImageData(mImageItem.getTitle());
            mIsGrayscale = mImageData.isGrayscale();
            mBlurAmount = mImageData.getBlurAmount();
            // TODO handle null ImageData
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
        mPhotoView = view.findViewById(R.id.edit_image);
        Size size = ImageManager.getPreferredImageSize();
        mProcessedBitmap = ImageManager.getInstance().getSmallBitmap(mImageItem, size.getWidth(), size.getHeight());
        mPhotoView.setImageBitmap(mProcessedBitmap);

        SeekBar blurControl = view.findViewById(R.id.blur_control_seekbar);
        blurControl.setMax(MAX_BLUR);
        blurControl.setProgress((mImageData.getBlurAmount() - 1) / 2);
        blurControl.setOnSeekBarChangeListener(mSeekBarChangedListener);

        ImageButton button = view.findViewById(R.id.bnw);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mIsGrayscale = !mImageData.isGrayscale();
                processImage(mBlurAmount, mIsGrayscale);
            }
        });

        mSaveButton = view.findViewById(R.id.confirm_change);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageData.setGrayscale(mIsGrayscale);
                mImageData.setBlurAmount(mBlurAmount);
                ImageManager.getInstance().cacheBitmap(mImageItem.getTitle(), mProcessedBitmap);

                mBackgroundRunner.run(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap result = Bitmap.createBitmap(mImageData.getOriginalImage());
                        ImageUtils.applyMask(mImageData.getOriginalImage(),
                                result,
                                mImageData.getMask(),
                                mImageData.getMaskWidth(),
                                mImageData.getMaskHeight(),
                                mImageData.getBlurAmount(),
                                mImageData.isGrayscale());
                        ImageManager.getInstance().saveBitmap(mImageItem.getTitle(), result);
                    }
                });
                getActivity().getSupportFragmentManager().popBackStackImmediate();
            }
        });
        mSaveButton.setEnabled(false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof RunInBackgroundListener) {
            mBackgroundRunner = (RunInBackgroundListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement RunInBackgroundListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mBackgroundRunner = null;
    }

    private void processImage(int blurAmount, boolean grayscale) {
        Bitmap image = Bitmap.createScaledBitmap(
                mImageData.getOriginalImage(), mProcessedBitmap.getWidth(), mProcessedBitmap.getHeight(), true);

        ImageUtils.applyMask(image,
                mProcessedBitmap,
                mImageData.getMask(),
                mImageData.getMaskWidth(),
                mImageData.getMaskHeight(),
                blurAmount,
                grayscale);

        mPhotoView.setImageBitmap(mProcessedBitmap);
        mSaveButton.setEnabled(true);
    }
}
