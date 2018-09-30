package com.example.chin.instancesegmentation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ImageReader;

import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

import com.example.chin.instancesegmentation.env.Logger;
import com.example.chin.instancesegmentation.env.ImageUtils;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final boolean SAVE_PREVIEW_BITMAP = false;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE = 0.4f;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private Classifier mDetector;
    private Classifier mClassifier;

    private Integer mSensorOrientation;

    private int mCropWidth;
    private int mCropHeight;

    private Bitmap mRgbFrameBitmap = null;
    private Bitmap mCroppedBitmap = null;
    private Bitmap mCropCopyBitmap = null;

    private Bitmap mRgbPreviewBitmap = null;
    private Bitmap mCroppedPreviewBitmap = null;

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

    public native void process(long imgAddr, long maskAddr, long resultAddr, int previewWidth, int previewHeight);
    public native void bokeh(long imgAddr, long maskAddr, long resultAddr, int previewWidth, int previewHeight);


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

        mTrackingOverlay = findViewById(R.id.tracking_overlay);
        mTrackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) { }
                });

        addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        if (!isDebug()) {
                            return;
                        }

                        final Bitmap copy = mCropCopyBitmap;
                        if (copy == null) {
                            return;
                        }

                        final int backgroundColor = Color.argb(100, 0, 0, 0);
                        canvas.drawColor(backgroundColor);

                        final Matrix matrix = new Matrix();
                        final float scaleFactor = 2;
                        matrix.postScale(scaleFactor, scaleFactor);
                        matrix.postTranslate(
                                canvas.getWidth() - copy.getWidth() * scaleFactor,
                                canvas.getHeight() - copy.getHeight() * scaleFactor);
                        canvas.drawBitmap(copy, matrix, new Paint());

                        /*
                        final Vector<String> lines = new Vector<String>();
                        if (mDetector != null) {
                            final String statString = mDetector.getStatString();
                            final String[] statLines = statString.split("\n");
                            for (final String line : statLines) {
                                lines.add(line);
                            }
                        }
                        */
                    }
                });
    }

    @Override
    public void onPictureSizeChosen(final Size size, final int rotation) {
        if (!mInitialised) {
            initialiseDetectors();
        }

        mPictureWidth = size.getWidth();
        mPictureHeight = size.getHeight();

        mSensorOrientation = rotation - getScreenOrientation();

        mRgbFrameBitmap = Bitmap.createBitmap(mPictureWidth, mPictureHeight, Bitmap.Config.ARGB_8888);

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

        final Long timeStamp = System.currentTimeMillis();
        final String filename = "IMG_" + timeStamp.toString() + ".png";
        ImageManager.getInstance().addPendingImage(filename);

        runInBackground(new Runnable() {
            @Override
            public void run() {

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
                final Bitmap rgbFrameBitmapRotated = Bitmap.createBitmap(mRgbFrameBitmap,
                        0,
                        0,
                        mRgbFrameBitmap.getWidth(),
                        mRgbFrameBitmap.getHeight(),
                        mFrameToCropTransform,
                        true);

                final int w = rgbFrameBitmapRotated.getWidth();
                final int h = rgbFrameBitmapRotated.getHeight();

                // Resize to a smaller image for faster inference.
                final float scaleFactor = (float)MAX_SIZE / Math.max(w, h);
                mCropWidth = Math.round(scaleFactor * w);
                mCropHeight = Math.round(scaleFactor * h);

                mCroppedBitmap =
                        Bitmap.createScaledBitmap(rgbFrameBitmapRotated, mCropWidth, mCropHeight, true);

                readyForNextImage();

                final long mid1 = System.nanoTime();
                long dur1 = (mid1 - start) / 1000000 ;
                LOGGER.i("Preparing bitmap took " + dur1 + " ms");

                final List<Classifier.Recognition> results = mDetector.recognizeImage(mCroppedBitmap);

                long mid2 = System.nanoTime();
                long dur2 = (mid2 - mid1) /1000000 ;
                LOGGER.i("Detection took " + dur2 + " ms");

                if (results.size() == 0)
                    return;

                Classifier.Recognition result = results.get(0);
                int[] mask = result.getMask();

                Bitmap resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                ImageUtils.applyMask(rgbFrameBitmapRotated, resultBitmap, mask, mCropWidth, mCropHeight, false);

                long mid3 = System.nanoTime();
                long dur3 = (mid3 - mid2) / 1000000;
                LOGGER.i("Post processing took " + dur3 + " ms");

                ImageManager.getInstance().cacheBitmap(
                        filename, resultBitmap, rgbFrameBitmapRotated, mask, mCropWidth, mCropHeight);
                onProcessingComplete(filename);
                ImageManager.getInstance().saveBitmap(filename, resultBitmap);

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
