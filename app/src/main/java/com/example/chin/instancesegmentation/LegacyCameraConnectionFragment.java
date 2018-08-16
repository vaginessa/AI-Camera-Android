package com.example.chin.instancesegmentation;

/*
 * Copyright 2017 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import java.io.IOException;
import java.util.List;

import com.example.chin.instancesegmentation.env.Logger;
import com.example.chin.instancesegmentation.env.ImageUtils;

public class LegacyCameraConnectionFragment extends CameraFragment {
    private Camera mCamera;
    private static final Logger LOGGER = new Logger();
    private final Camera.PreviewCallback mImageListener;
    private final Camera.PreviewCallback mPreviewImageListener;
    private final Size mDesiredSize;

    /**
     * The layout identifier to inflate for this Fragment.
     */
    private final int mLayout;

    /**
     * An {@link AutoFitTextureView} for mCamera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private LegacyCameraConnectionFragment(
            final Camera.PreviewCallback imageListener,
            final Camera.PreviewCallback previewImageListener,
            final int layout,
            final Size desiredSize) {

        mImageListener = imageListener;
        mPreviewImageListener = previewImageListener;
        mLayout = layout;
        mDesiredSize = desiredSize;
    }

    public static LegacyCameraConnectionFragment newInstance(
            final Camera.PreviewCallback imageListener,
            final Camera.PreviewCallback previewImageListener,
            final int layout,
            final Size desiredSize) {

        return new LegacyCameraConnectionFragment(
                imageListener, previewImageListener, layout, desiredSize);
    }

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * {@link android.view.TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera(texture);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    stopCamera();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    private void openCamera(final SurfaceTexture texture) {
        int index = getCameraId();
        mCamera = Camera.open(index);

        try {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null
                    && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
            Size[] sizes = new Size[cameraSizes.size()];
            int i = 0;
            for (Camera.Size size : cameraSizes) {
                sizes[i++] = new Size(size.width, size.height);
            }
            Size previewSize =
                    CameraConnectionFragment.chooseOptimalSize(
                            sizes, mDesiredSize.getWidth(), mDesiredSize.getHeight());
            parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());


            LOGGER.i("Chosen preview size: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();
            Size[] picSizes = new Size[pictureSizes.size()];

            i = 0;
            for (Camera.Size size : pictureSizes) {
                picSizes[i++] = new Size(size.width, size.height);
            }

            Size pictureSize = CameraConnectionFragment.chooseLargestSize(picSizes);

            LOGGER.i("Chosen picture size: " + pictureSize.getWidth() + "x" + pictureSize.getHeight());
            parameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());

            mCamera.setDisplayOrientation(90);
            mCamera.setParameters(parameters);
            mCamera.setPreviewTexture(texture);
        } catch (IOException exception) {
            mCamera.release();
        }

        mCamera.setPreviewCallbackWithBuffer(mPreviewImageListener);
        Camera.Size s = mCamera.getParameters().getPreviewSize();
        mCamera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);

        mTextureView.setAspectRatio(s.height, s.width);

        mCamera.startPreview();
    }

    private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, final Camera camera) {
            camera.stopPreview();
            final byte[] bytes = data.clone();
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    camera.startPreview();
                    mImageListener.onPreviewFrame(bytes, camera);

                    /*
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inMutable = true;
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);

                    // Need to rotate it and all that.
                    final Long timestamp = System.currentTimeMillis();
                    ImageUtils.saveBitmap(bmp, "IMG_" + timestamp.toString() + ".png");
                    */
                }
            });
        }
    };

    @Override
    public View onCreateView(
            final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        View view = inflater.inflate(mLayout, container, false);

        ImageButton button = view.findViewById(R.id.picture);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.takePicture(null, null, mPictureCallback);
            }
        });

        ImageButton galleryButton = view.findViewById(R.id.goto_gallery);
        galleryButton.setOnClickListener(mOnCameraButtonClicked);

        return view;
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            mTextureView.setVisibility(View.VISIBLE);
            if (mCamera == null) {
                openCamera(mTextureView.getSurfaceTexture());
            }
            mCamera.startPreview();
        } else {
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }

        ImageManager.getInstance().clearCachedBitmap();
    }

    @Override
    public void onPause() {
        stopCamera();
        mTextureView.setVisibility(View.GONE);
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    protected void stopCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }

        if (mTextureView != null) {
        }
    }

    private int getCameraId() {
        CameraInfo ci = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == CameraInfo.CAMERA_FACING_BACK)
                return i;
        }
        return -1; // No mCamera found
    }
}
