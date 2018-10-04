package com.example.chin.instancesegmentation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ImageReader;
import android.util.Size;
import android.widget.Toast;

import com.example.chin.instancesegmentation.env.ImageUtils;
import com.example.chin.instancesegmentation.env.Logger;

import java.io.IOException;
import java.util.List;

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final boolean SAVE_PREVIEW_BITMAP = false;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE = 0.4f;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private Classifier mDetector;
    private Classifier mClassifier;

    private Integer mSensorOrientation;

    private Bitmap mRgbFrameBitmap = null;
    private Bitmap mOriginalBitmap = null;
    private Bitmap mDisplayBitmap = null;
    private Bitmap mInferenceBitmap = null;

    private Bitmap mRgbPreviewBitmap = null;
    private Bitmap mCroppedPreviewBitmap = null;

    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mInferenceWidth;
    private int mInferenceHeight;

    private byte[] mLuminanceCopy;

    private Matrix mFrameToCropTransform;
    private Matrix mCropToFrameTransform;
    private Matrix mFrameToClassifyTransform;

    private long mTimestamp = 0;
    private long mLastProcessingTimeMs;
    private boolean mComputingDetection = false;
    private boolean mInitialised = false;

    private OverlayView mTrackingOverlay;

    // These are for the real time classifier.
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";

    private static final String CLASSIFIER_MODEL_FILE =
            "file:///android_asset/tensorflow_inception_graph.pb";
    private static final String CLASSIFIER_LABELS_FILE =
            "file:///android_asset/imagenet_comp_graph_label_strings.txt";

    // DeepLab Config
    private static final String DEEPLAB_MODEL_FILE =
            "file:///android_asset/deeplabv3_mobilenetv2_pascal_mod.pb";
    private static final int DEEPLAB_IMAGE_SIZE = 513;

    // ShuffleSeg configs
    private static final String SHUFFLESEG_MODEL_FILE = "file:///android_asset/shuffleseg_pascal.pb";
    private static final String SHUFFLESEG_INPUT_NAME = "image_tensor";
    private static final String[] SHUFFLESEG_OUTPUT_NAMES = { "output_mask" };
    private static final int MAX_SIZE = 500; // Length of the longest edge.

    // Default post processing settings.
    private final int mBlurAmount = 9;
    private final boolean mGrayscale = true;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        if (!mInitialised) {
            initialiseDetectors();
        }

        mPreviewWidth = size.getWidth();
        mPreviewHeight = size.getHeight();

        mSensorOrientation = rotation - getScreenOrientation();

        LOGGER.i("Camera orientation relative to screen canvas: %d", mSensorOrientation);
        LOGGER.i("Initializing at size %dx%d", mPreviewWidth, mPreviewHeight);

        mRgbPreviewBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888);
        mCroppedPreviewBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);

        mFrameToClassifyTransform = ImageUtils.getTransformationMatrix(
                mPreviewWidth, mPreviewHeight,
                INPUT_SIZE, INPUT_SIZE,
                mSensorOrientation, true);
    }

    @Override
    public void onPictureSizeChosen(final Size size, final int rotation) {
        if (!mInitialised) {
            initialiseDetectors();
        }

        mSensorOrientation = rotation - getScreenOrientation();
        Size preferredSize = ImageManager.getPreferredImageSize();

        mPictureWidth = size.getWidth();
        mPictureHeight = size.getHeight();

        float scaleFactor = Math.max(preferredSize.getHeight(), preferredSize.getWidth()) /
                (float)Math.max(mPictureWidth, mPictureHeight);

        mDisplayWidth = Math.round(scaleFactor * mPictureWidth);
        mDisplayHeight = Math.round(scaleFactor * mPictureHeight);

        if (Math.abs(rotation) == 90) {
            int temp = mDisplayHeight;
            mDisplayHeight = mDisplayWidth;
            mDisplayWidth = temp;
        }

        float scaleFactor2 = MAX_SIZE / (float)Math.max(mDisplayWidth, mDisplayHeight);
        mInferenceWidth = Math.round(scaleFactor2 * mDisplayWidth);
        mInferenceHeight = Math.round(scaleFactor2 * mDisplayHeight);

        mRgbFrameBitmap = Bitmap.createBitmap(mPictureWidth, mPictureHeight, Bitmap.Config.ARGB_8888);
        mDisplayBitmap = Bitmap.createBitmap(mDisplayWidth, mDisplayHeight, Bitmap.Config.ARGB_8888);
        mInferenceBitmap = Bitmap.createBitmap(mInferenceWidth, mInferenceHeight, Bitmap.Config.ARGB_8888);

        mCropToFrameTransform = new Matrix();
        mFrameToCropTransform = ImageUtils.getTransformationMatrix(
                mPictureWidth, mPictureHeight,
                mPictureWidth, mPictureHeight,
                mSensorOrientation, true);

        mFrameToCropTransform.invert(mCropToFrameTransform);
    }

    private void initialiseDetectors() {
        try {
            if (mDetector == null) {
                mDetector =
                        SegmentationModel.Create(
                                getAssets(),
                                SHUFFLESEG_MODEL_FILE,
                                "",
                                SHUFFLESEG_INPUT_NAME,
                                SHUFFLESEG_OUTPUT_NAMES);
            }

            if (mClassifier == null) {
                mClassifier =
                        TensorFlowImageClassifier.create(
                                getAssets(),
                                CLASSIFIER_MODEL_FILE,
                                CLASSIFIER_LABELS_FILE,
                                INPUT_SIZE,
                                IMAGE_MEAN,
                                IMAGE_STD,
                                INPUT_NAME,
                                OUTPUT_NAME);
            }

            mInitialised = true;
        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(),
                            "Classifier could not be initialized",
                            Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
    }

    protected void processPreview() {
        readyForNextPreviewImage();
    }

    @Override
    protected void processImage() {
        LOGGER.i("Process still");

        runInBackground(new Runnable() {
            @Override
            public void run() {
                final String filename = mFilename;

                /*
                mRgbFrameBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.nayeon);
                mPictureHeight = mRgbFrameBitmap.getHeight();
                mPictureWidth = mRgbFrameBitmap.getWidth();
                */

                // Different ways of retrieving the image depending on the API used.
                if (mUseCamera2API) {
                    mRgbFrameBitmap.setPixels(
                            getRgbBytes(), 0, mPictureWidth, 0, 0, mPictureWidth, mPictureHeight);
                } else {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inMutable = true;
                    byte[] bytes = getPictureBytes();
                    mRgbFrameBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                }

                long start = System.nanoTime();

                // Rotate image to the correct orientation.
                mOriginalBitmap = Bitmap.createBitmap(mRgbFrameBitmap,
                        0,
                        0,
                        mRgbFrameBitmap.getWidth(),
                        mRgbFrameBitmap.getHeight(),
                        mFrameToCropTransform,
                        true);

                mRgbFrameBitmap.recycle();

                // Resize to a smaller image for faster post processing. The size is large enough to
                // still look good for the resolution of the screen.
                mDisplayBitmap = Bitmap.createScaledBitmap(mOriginalBitmap, mDisplayWidth, mDisplayHeight, true);

                // Resize to a smaller image for faster inference.
                mInferenceBitmap = Bitmap.createScaledBitmap(mDisplayBitmap, mInferenceWidth, mInferenceHeight, true);

                readyForNextImage();

                final long mid1 = System.nanoTime();
                long dur1 = (mid1 - start) / 1000000 ;
                LOGGER.i("Preparing bitmap took " + dur1 + " ms");

                final List<Classifier.Recognition> results = mDetector.recognizeImage(mInferenceBitmap);

                long mid2 = System.nanoTime();
                long dur2 = (mid2 - mid1) /1000000 ;
                LOGGER.i("Detection took " + dur2 + " ms");

                if (results.size() == 0)
                    return;

                Classifier.Recognition result = results.get(0);
                int[] mask = result.getMask();

                ImageUtils.applyMask(mDisplayBitmap, mDisplayBitmap, mask, mInferenceWidth, mInferenceHeight, mBlurAmount, mGrayscale);

                long mid3 = System.nanoTime();
                long dur3 = (mid3 - mid2) / 1000000;
                LOGGER.i("Post processing took " + dur3 + " ms");

                ImageManager.getInstance().cacheBitmap(filename, mDisplayBitmap);
                ImageData imageData = new ImageData(mOriginalBitmap, mask, mInferenceWidth, mInferenceHeight, mBlurAmount, mGrayscale);
                ImageManager.getInstance().storeImageData(filename, imageData);
                onProcessingComplete(filename);

                ImageUtils.applyMask(mOriginalBitmap, mOriginalBitmap, mask, mInferenceWidth, mInferenceHeight, mBlurAmount, mGrayscale);
                ImageManager.getInstance().saveBitmap(filename, mOriginalBitmap);

                showToast("Saved");

                long mid4 = System.nanoTime();
                long dur4 = (mid4 - mid3) / 1000000;
                LOGGER.i("Saving to file took " + dur4 + " ms");

                mComputingDetection = false;
            }
        });
    }

    private void RecogniseObject() {
        /*
        mRgbPreviewBitmap.setPixels(
                getRgbBytesPreview(), 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);

        final Canvas canvas = new Canvas(mCroppedPreviewBitmap);
        canvas.drawBitmap(mRgbPreviewBitmap, mFrameToClassifyTransform, null);

        runInBackground(new Runnable() {
            @Override
            public void run() {
                final List<Classifier.Recognition> results = mClassifier.recognizeImage(mCroppedPreviewBitmap);
                if (results.size() > 0) {
                    final Classifier.Recognition result = results.get(0);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = findViewById(R.id.label);
                            if (textView != null) {
                                if (result.getConfidence() >= MINIMUM_CONFIDENCE) {
                                    textView.setText(result.getTitle());
                                } else {
                                    textView.setText("");
                                }
                            }
                        }
                    });
                }
                readyForNextPreviewImage();
            }
        });
        */
    }

    private void onProcessingComplete(final String filename) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyFragmentOfImageChange(filename);
            }
        });
    }

    private void showToast(final String text) {
        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.fragment_camera_connection;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onSetDebug(final boolean debug) {
        mDetector.enableStatLogging(debug);
    }
}
