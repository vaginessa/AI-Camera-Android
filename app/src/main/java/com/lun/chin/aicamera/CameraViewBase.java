package com.lun.chin.aicamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Size;
import android.view.TextureView;

import com.lun.chin.aicamera.env.Logger;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class CameraViewBase extends AutoFitTextureView implements
    TextureView.SurfaceTextureListener {

    private static final Logger LOGGER = new Logger();

    protected CameraFrameAvailableListener mCameraFrameAvailableListener;
    protected boolean mUseFrontCamera = false;
    protected Size mDesiredSize;
    protected int mPreviewWidth;
    protected int mPreviewHeight;
    protected int mRotation = 90;
    protected float mScale;

    private Handler mHandler;
    private HandlerThread mHandlerThread;

    public CameraViewBase(Context context) {
        super(context);
        setSurfaceTextureListener(this);
    }

    public CameraViewBase(Context context, AttributeSet attr) {
        super(context, attr);
        setSurfaceTextureListener(this);
    }

    public CameraViewBase(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    public interface CameraFrameAvailableListener {
        Bitmap processPreview(CameraFrame frame);
        void processPicture(CameraFrame frame);
        void onCameraStarted(Size previewSize, Size pictureSize);
    }

    @Override
    public void onSurfaceTextureAvailable(
            final SurfaceTexture texture, final int width, final int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            final SurfaceTexture texture, final int width, final int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
        stopCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}

    protected void drawFrame(CameraFrame frame) {
        Canvas canvas = lockCanvas();
        if (canvas != null) {
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            Bitmap bitmap = mCameraFrameAvailableListener.processPreview(frame);
            int width = (int)(bitmap.getWidth() * mScale);
            int height = (int)(bitmap.getHeight() * mScale);

            Rect dst = new Rect((canvas.getWidth() - width) / 2,
                    (canvas.getHeight() - height) / 2,
                    (canvas.getWidth() - width) / 2 + width,
                    (canvas.getHeight() - height) / 2 + height);

            canvas.drawBitmap(bitmap, null, dst, null);
            unlockCanvasAndPost(canvas);
        }
    }

    public void setProcessPreviewListener(CameraFrameAvailableListener listener) {
        mCameraFrameAvailableListener = listener;
    }

    public void setDesiredSize(Size size) {
        mDesiredSize = size;
    }

    protected void runInBackground(Runnable runnable) {
        mHandler.post(runnable);
    }

    protected void startBackgroundThread() {
        mHandlerThread = new HandlerThread("CameraBackground");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    protected void removeBackgroundTasks(Runnable r) {
        if (mHandler != null) {
            mHandler.removeCallbacks(r);
        }
    }

    protected void stopBackgroundThread() {
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param desiredSize The preferred size for the camera frames.
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    protected static Size chooseOptimalSize(final Size[] choices, final Size desiredSize) {
        final int minSize = Math.min(desiredSize.getWidth(), desiredSize.getHeight());

        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        LOGGER.i("Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
        LOGGER.i("Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
        LOGGER.i("Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

        if (exactSizeFound) {
            LOGGER.i("Exact size match found.");
            return desiredSize;
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            LOGGER.e("Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Size chooseLargestSize(final Size[] choices) {
        LOGGER.i("Available picture sizes: [" + TextUtils.join(", ", choices) + "]");
        return Collections.max(Arrays.asList(choices), new CompareSizesByArea());
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    protected abstract void openCamera();
    protected abstract void stopCamera();
    protected abstract void pauseCamera();
    protected abstract void resumeCamera();
    protected abstract void switchCamera();
    protected abstract void takePicture();

    public class CameraFrame {

        private int mImageFormat;
        private Mat mData;
        private Mat mRgb;
        private int mWidth;
        private int mHeight;
        private int mRotation;

        public CameraFrame(Mat data, int width, int height, int rotation, int format) {
            mWidth = width;
            mHeight = height;
            mRotation = rotation;
            mData = data;
            mRgb = new Mat();
            mImageFormat = format;
        }

        private void rotate(Mat src, Mat dst) {
            if (mRotation == 90)
                Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE);
            else if (mRotation == 180)
                Core.rotate(src, dst, Core.ROTATE_180);
            else if (mRotation == 270)
                Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE);
        }

        public Mat gray() {
            Mat gray = mData.submat(0, mHeight, 0, mWidth);
            rotate(gray, gray);
            return gray;
        }

        public Mat rgb() {
            if (mImageFormat == ImageFormat.NV21)
                Imgproc.cvtColor(mData, mRgb, Imgproc.COLOR_YUV420sp2RGB, 3);
            else if (mImageFormat == ImageFormat.JPEG)
                Imgproc.cvtColor(mData, mRgb, Imgproc.COLOR_BGR2RGB);
            else
                throw new IllegalArgumentException("Format must be either NV21 or JPEG");

            rotate(mRgb, mRgb);

            return mRgb;
        }

        public int Width() { return mWidth; }
        public int Height() { return mHeight; }

        public void release() {
            mData.release();
            mRgb.release();
        }
    }
}
