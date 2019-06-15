package com.lun.chin.aicamera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Size;

import com.lun.chin.aicamera.env.ImageUtils;
import com.lun.chin.aicamera.env.Logger;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.util.List;

public class CameraView extends CameraViewBase implements Camera.PreviewCallback{
    private static final Logger LOGGER = new Logger();

    private Camera mCamera;
    private Mat[] mFrameChain;
    private CameraFrame[] mCameraFrames;
    private int mChainIdx = 0;
    private SurfaceTexture mDummyTexture;

    Runnable mProcessPreview = new Runnable() {
        @Override
        public void run() {
            drawFrame(mCameraFrames[1 - mChainIdx]);
        }
    };

    public CameraView(Context context) {
        super(context);
    }

    public CameraView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public CameraView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void openCamera() {
        LOGGER.d("openCamera");
        int index = mUseFrontCamera ? getFrontCameraId() : getCameraId();

        if (mCamera != null) {
            releaseCamera();
        }

        try {
            mCamera = Camera.open(index);
        } catch (Exception ex) {
            LOGGER.e(ex, "Unable to open camera");
            return;
        }

        try {
            Camera.Parameters parameters = mCamera.getParameters();

            // Set camera focus mode.
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null
                    && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }

            // Determine the best preview size.
            List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
            Size[] sizes = new Size[cameraSizes.size()];
            int i = 0;
            for (Camera.Size size : cameraSizes) {
                sizes[i++] = new Size(size.width, size.height);
            }
            Size previewSize = CameraViewBase.chooseOptimalSize(sizes, mDesiredSize);
            parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
            LOGGER.i("Chosen preview size: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            // Determine the largest picture size.
            List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();
            Size[] picSizes = new Size[pictureSizes.size()];
            i = 0;
            for (Camera.Size size : pictureSizes) {
                picSizes[i++] = new Size(size.width, size.height);
            }

            Size pictureSize = CameraViewBase.chooseLargestSize(picSizes);
            parameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
            LOGGER.i("Chosen picture size: " + pictureSize.getWidth() + "x" + pictureSize.getHeight());

            mCamera.setParameters(parameters);
            Camera.Size s = mCamera.getParameters().getPreviewSize();
            Camera.Size ps = mCamera.getParameters().getPictureSize();
            mPreviewWidth = s.width;
            mPreviewHeight = s.height;
            // Scaling needed to fit the preview to the screen.
            mScale = (float)getWidth() / mPreviewHeight;

            mFrameChain = new Mat[2];
            mFrameChain[0] = new Mat(mPreviewHeight + (mPreviewHeight/2), mPreviewWidth, CvType.CV_8UC1);
            mFrameChain[1] = new Mat(mPreviewHeight + (mPreviewHeight/2), mPreviewWidth, CvType.CV_8UC1);

            mCameraFrames = new CameraFrame[2];
            mCameraFrames[0] = new CameraFrame(mFrameChain[0], mPreviewWidth, mPreviewHeight, mRotation, ImageFormat.NV21);
            mCameraFrames[1] = new CameraFrame(mFrameChain[1], mPreviewWidth, mPreviewHeight, mRotation, ImageFormat.NV21);

            mCamera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(mPreviewHeight, mPreviewWidth)]);
            mCamera.setPreviewCallbackWithBuffer(this);

            mDummyTexture = new SurfaceTexture(10);
            mCamera.setPreviewTexture(mDummyTexture);
            mCamera.startPreview();

            setAspectRatio(mPreviewHeight, mPreviewWidth);
            startBackgroundThread();

            mCameraFrameAvailableListener.onCameraStarted(
                    new Size(s.width, s.height),
                    new Size(ps.width, ps.height));

        } catch (IOException exception) {
            mCamera.release();
        }
    }

    @Override
    protected void stopCamera() {
        LOGGER.d("stopCamera");
        stopBackgroundThread();
        releaseCamera();
    }

    @Override
    protected void pauseCamera() {
        LOGGER.d("Pause camera preview.");
        if (mCamera != null) {
            mCamera.stopPreview();
            removeBackgroundTasks(mProcessPreview);
        }
    }

    @Override
    protected void resumeCamera() {
        LOGGER.d("Resume camera preview.");
        if (mCamera != null) {
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.startPreview();
        } else {
            openCamera();
        }
        startBackgroundThread();
    }

    @Override
    protected void switchCamera() {
        mUseFrontCamera = !mUseFrontCamera;
        mRotation = mUseFrontCamera ? 270 : 90;
        openCamera();
    }

    protected void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
        removeBackgroundTasks(mProcessPreview);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mFrameChain[mChainIdx].put(0, 0, data);
        mChainIdx = 1 - mChainIdx;
        runInBackground(mProcessPreview);
        mCamera.addCallbackBuffer(data);
    }

    @Override
    protected void takePicture() {
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                camera.stopPreview();
                removeBackgroundTasks(mProcessPreview);
                Camera.Size size = camera.getParameters().getPictureSize();
                Mat mat = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
                CameraFrame frame = new CameraFrame(mat, size.width, size.height, mRotation, ImageFormat.JPEG);
                mCameraFrameAvailableListener.processPicture(frame);
                resumeCamera();
            }
        });
    }

    private int getCameraId() {
        Camera.CameraInfo ci = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
                return i;
        }
        return -1; // No mCamera found
    }

    private int getFrontCameraId() {
        Camera.CameraInfo ci = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                return i;
        }
        return -1;
    }
}
