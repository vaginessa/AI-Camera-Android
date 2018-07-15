package com.example.chin.instancesegmentation;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ImageReader;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.Type;
import android.util.Size;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.example.chin.instancesegmentation.env.Logger;
import com.example.chin.instancesegmentation.env.ImageUtils;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final String MODEL_FILE = "file:///android_asset/mask_rcnn_coco_mod.pb";
    private static final String LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

    private static final boolean SAVE_PREVIEW_BITMAP = false;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE = 0.9f;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private Classifier mDetector;

    private Integer mSensorOrientation;

    private Bitmap mRgbFrameBitmap = null;
    private Bitmap mCroppedBitmap = null;
    private Bitmap mCropCopyBitmap = null;

    private byte[] mLuminanceCopy;

    private Matrix mFrameToCropTransform;
    private Matrix mCropToFrameTransform;

    private long mTimestamp = 0;
    private long mLastProcessingTimeMs;

    private OverlayView mTrackingOverlay;

    public native void process(long imgAddr, long maskAddr, long resultAddr, int previewWidth, int previewHeight);

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        try {
            if (mDetector == null) {
                mDetector = MaskRCNN.create(getAssets(), MODEL_FILE, LABELS_FILE);
            }
        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        mPreviewWidth = size.getWidth();
        mPreviewHeight = size.getHeight();
        int cropSize = mPreviewWidth > mPreviewHeight ? mPreviewWidth : mPreviewHeight;

        mSensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", mSensorOrientation);

        LOGGER.i("Initializing at size %dx%d", mPreviewWidth, mPreviewHeight);
        mRgbFrameBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888);
        mCroppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        mFrameToCropTransform = new Matrix();
        mCropToFrameTransform = new Matrix();
        mFrameToCropTransform.postRotate(mSensorOrientation);
        mFrameToCropTransform.invert(mCropToFrameTransform);

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
    protected void processImage() {
        ++mTimestamp;
        final long currTimestamp = mTimestamp;

        // This is temporary for testing.
        //mRgbFrameBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sana);
        //mPreviewHeight = mRgbFrameBitmap.getHeight();
        //mPreviewWidth = mRgbFrameBitmap.getWidth();

        mRgbFrameBitmap.setPixels(
                getRgbBytes(), 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);
        // Rotate image to the correct orientation.
        mRgbFrameBitmap = Bitmap.createBitmap(
                mRgbFrameBitmap, 0, 0, mPreviewWidth, mPreviewHeight, mFrameToCropTransform, true);
        // The MaskRCNN implementation expects a square image.
        mCroppedBitmap = createSquaredBitmap(mRgbFrameBitmap);

        final int cropSize = 300;
        mCroppedBitmap = Bitmap.createScaledBitmap(mCroppedBitmap, cropSize, cropSize, true);

        readyForNextImage();

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(mCroppedBitmap);
        }

        runInBackground(new Runnable() {
            @Override
            public void run() {
                LOGGER.i("Running detection on image " + currTimestamp);
                final List<Classifier.Recognition> results = mDetector.recognizeImage(mCroppedBitmap);

                if (results.size() == 0)
                    return;

                // Just take the first object recognised for now.
                final Classifier.Recognition result = results.get(0);
                final int[] mask = result.getMask();

                Mat img = new Mat();
                Utils.bitmapToMat(mRgbFrameBitmap, img);
                Mat maskMat = new Mat(cropSize, cropSize, CvType.CV_32SC1);
                Mat outImage = new Mat(img.size(), img.type());
                maskMat.put(0, 0, mask);

                // Refine mask and blur background.
                process(img.getNativeObjAddr(),
                        maskMat.getNativeObjAddr(),
                        outImage.getNativeObjAddr(),
                        mRgbFrameBitmap.getWidth(),
                        mRgbFrameBitmap.getHeight());

                Utils.matToBitmap(outImage, mRgbFrameBitmap);

                final Long timeStamp = System.currentTimeMillis();
                ImageUtils.saveBitmap(mRgbFrameBitmap, "IMG_" + timeStamp.toString() + ".png");
                showToast("Saved");
            }
        });
    }

    /*
    @Override
    protected void processImage() {
        ++mTimestamp;
        final long currTimestamp = mTimestamp;
        byte[] originalLuminance = getLuminance();

        // No mutex needed as this method is not reentrant.
        if (mComputingDetection) {
            readyForNextImage();
            return;
        }
        mComputingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        //mRgbFrameBitmap.setPixels(getRgbBytes(), 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);

        mRgbFrameBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mug);
        mRgbFrameBitmap = createSquaredBitmap(mRgbFrameBitmap);

        if (mLuminanceCopy == null) {
            mLuminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, mLuminanceCopy, 0, originalLuminance.length);
        readyForNextImage();

        int cropSize = mRgbFrameBitmap.getWidth();
        mCroppedBitmap = Bitmap.createBitmap(mRgbFrameBitmap, 0, 0, cropSize, cropSize, mFrameToCropTransform, true);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(mCroppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = mDetector.recognizeImage(mCroppedBitmap);
                        mLastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        // This is for drawing bounding boxes on the cropped bitmap for debugging.
                        mCropCopyBitmap = Bitmap.createBitmap(mCroppedBitmap);
                        final Canvas canvas = new Canvas(mCropCopyBitmap);

                        // This is for drawing bounding boxes on the un-cropped bitmap.
                        final Canvas canvas2 = new Canvas(mRgbFrameBitmap);

                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        final List<RectF> locations = new ArrayList<>();

                        for (final Classifier.Recognition result : results) {
                            if (result.getConfidence() >= MINIMUM_CONFIDENCE) {
                                final RectF location = result.getLocation();

                                if (location != null) {
                                    // Transform the bounding box to frame coordinates.
                                    //mCropToFrameTransform.mapRect(location);
                                    //canvas2.drawRect(location, paint);
                                    locations.add(location);
                                }
                            }
                        }

                        Bitmap rgbFrameBitmapCopy = Bitmap.createBitmap(mRgbFrameBitmap);

                        for (final Classifier.Recognition result : results) {
                            if (result.getConfidence() >= MINIMUM_CONFIDENCE) {
                                final RectF currentLocation = result.getLocation();
                                final int[] mask = result.getMask();
                                //float[] vec = pointsToVector(mask);
                                //mCropToFrameTransform.mapPoints(vec);
                                //int[] transformedMask = vecToPoints(vec, mPreviewWidth, mPreviewHeight);

                                // TODO: Make this into a function in ImageUtils.
                                for (int i = 0; i < mRgbFrameBitmap.getWidth(); ++i) {
                                    for (int j = 0; j < mRgbFrameBitmap.getHeight(); ++j) {
                                        boolean inside = false;
                                        for (final RectF location : locations) {
                                            inside |= location.contains(i, j);
                                        }

                                        if (!inside) {
                                            mRgbFrameBitmap.setPixel(i, j, Color.TRANSPARENT);
                                        }

                                        if (currentLocation.contains(i, j)) {
                                            int activate = mask[j * mRgbFrameBitmap.getHeight() + i];
                                            if (activate == 0) {
                                                mRgbFrameBitmap.setPixel(i, j, Color.TRANSPARENT);
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                        }

                        rgbFrameBitmapCopy = blur(rgbFrameBitmapCopy, 7);
                        overlay(rgbFrameBitmapCopy, mRgbFrameBitmap);

                        Bitmap result = Bitmap.createBitmap(rgbFrameBitmapCopy,
                                rgbFrameBitmapCopy.getWidth() - mPreviewHeight + 5,
                                0,
                                mPreviewHeight - 5,
                                mPreviewWidth);

                        Long timeStamp = System.currentTimeMillis();

                        ImageUtils.saveBitmap(result, "IMG_" + timeStamp.toString() + ".png");
                        showToast("Saved");

                        mTrackingOverlay.postInvalidate();
                        requestRender();
                        mComputingDetection = false;
                    }
                });
    }
    */

    private void showToast(final String text) {
        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static Bitmap createSquaredBitmap(Bitmap srcBmp) {
        int dim = Math.max(srcBmp.getWidth(), srcBmp.getHeight());
        Bitmap dstBmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(dstBmp);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(srcBmp, 0, 0, null);

        return dstBmp;
    }

    private void overlay(Bitmap bitmap, Bitmap overlay) {
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(overlay, 0, 0, paint);
    }

    private Bitmap blur(Bitmap bitmap, float radius) {
        RenderScript rs = RenderScript.create(this);

        //Create allocation from Bitmap
        Allocation allocation = Allocation.createFromBitmap(rs, bitmap);

        Type t = allocation.getType();

        //Create allocation with the same type
        Allocation blurredAllocation = Allocation.createTyped(rs, t);

        //Create script
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        //Set blur radius (maximum 25.0)
        blurScript.setRadius(radius);
        //Set input for script
        blurScript.setInput(allocation);
        //Call script for output allocation
        blurScript.forEach(blurredAllocation);

        //Copy script result into bitmap
        blurredAllocation.copyTo(bitmap);

        //Destroy everything to free memory
        allocation.destroy();
        blurredAllocation.destroy();
        blurScript.destroy();
        //t.destroy();
        rs.destroy();

        return bitmap;
    }

    private int turnGrayScale(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);

        int average = (r + g + b) / 3;
        return Color.rgb(average, average, average);
    }

    private float[] pointsToVector(int[] points) {
        int size = (int)Math.sqrt(points.length);
        List<Float> vec = new ArrayList<Float>();

        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size; ++j) {
                if (points[j * size + i] == 1) {
                    vec.add((float)i);
                    vec.add((float)j);
                }
            }
        }

        float[] result = new float[vec.size()];
        int i = 0;
        for (Float v : vec) {
            result[i++] = v;
        }

        return result;
    }

    private int[] vecToPoints(float[] vec, int width, int height) {
        int[] result = new int[width * height];
        for (int i = 0; i < vec.length; i += 2) {
            int x = (int)vec[i];
            int y = (int)vec[i + 1];

            result[y * height + x] = 1;
        }

        return result;
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
