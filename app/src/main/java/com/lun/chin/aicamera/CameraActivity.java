package com.lun.chin.aicamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.lun.chin.aicamera.env.ImageUtils;
import com.lun.chin.aicamera.env.Logger;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.List;

public class CameraActivity extends AppCompatActivity
        implements CameraViewBase.CameraFrameAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final int INFERENCE_SIZE = 200; // Length of the longest edge.
    private static final int PICTURE_INFERENCE_SIZE = 500; // Length of the longest edge.

    // ShuffleSeg configs.
    private static final String SHUFFLESEG_MODEL_FILE = "file:///android_asset/shuffleseg_human_cat_dog_39618.pb";
    private static final String SHUFFLESEG_INPUT_NAME = "image_tensor";
    private static final String[] SHUFFLESEG_OUTPUT_NAMES = { "output_mask" };

    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private CameraViewBase mCameraView;
    private Classifier mDetector;

    // Variables for the preview.
    private Bitmap mBitmap;
    private Mat mMat;
    private Mat mInferenceMat;
    private int mInferenceWidth;
    private int mInferenceHeight;

    // Dimensions for the picture.
    private int mPictureInferenceHeight;
    private int mPictureInferenceWidth;
    private int mDisplayWidth;
    private int mDisplayHeight;

    private String mFilename;
    private boolean mPausePreviewProcessing = false;

    private final int mBlurAmount = 11;
    private final int mPreviewBlurAmount = 5;
    private final boolean mGrayScale = true;

    private BaseLoaderCallback _baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    LOGGER.d("OpenCV loaded successfully");
                    // Load ndk built module, as specified in moduleName in build.gradle
                    // after opencv initialization
                    System.loadLibrary("native-lib");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
            }
        }
    };

    private class CameraButtonsListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.take_picture:
                    final Long timeStamp = System.currentTimeMillis();
                    mFilename = "IMG_" + timeStamp.toString() + ".png";
                    ImageManager.getInstance().addPendingImage(mFilename);
                    mCameraView.takePicture();
                    break;
                case R.id.goto_gallery:
                    mCameraView.pauseCamera();
                    mCameraView.setVisibility(View.INVISIBLE);

                    GalleryViewPagerFragment galleryViewPagerFragment =
                            GalleryViewPagerFragment.newInstance(
                                    0, ImageManager.getInstance().getImageItems());

                    final FragmentManager fragmentManager = getSupportFragmentManager();

                    fragmentManager
                            .beginTransaction()
                            .replace(R.id.container, galleryViewPagerFragment)
                            .addToBackStack(null)
                            .commit();

                    fragmentManager.addOnBackStackChangedListener(
                            new FragmentManager.OnBackStackChangedListener() {
                                public void onBackStackChanged() {
                                    if (fragmentManager.getBackStackEntryCount() == 0) {
                                        mCameraView.setVisibility(View.VISIBLE);
                                        mCameraView.resumeCamera();
                                    }
                                }
                            });
                    break;
                case R.id.switch_camera:
                    mCameraView.switchCamera();
                    LOGGER.d("switch button");
                    break;
            }
        }
    }

    private CameraButtonsListener mCameraButtonsListener = new CameraButtonsListener();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);

        if (hasPermission()) {
            initialise();
        } else {
            requestPermission();
        }

        ImageButton takePictureButton = findViewById(R.id.take_picture);
        takePictureButton.setOnClickListener(mCameraButtonsListener);

        ImageButton galleryButton = findViewById(R.id.goto_gallery);
        galleryButton.setOnClickListener(mCameraButtonsListener);

        ImageButton switchCamera = findViewById(R.id.switch_camera);
        switchCamera.setOnClickListener(mCameraButtonsListener);
    }

    @Override
    public synchronized void onStart() {
        LOGGER.d("onStart " + this);
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        LOGGER.d("onResume " + this);
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            LOGGER.d("Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, _baseLoaderCallback);
        } else {
            LOGGER.d("OpenCV library found inside package. Using it!");
            _baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        mHandlerThread = new HandlerThread("inference");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        LOGGER.d("onPause " + this);
        super.onPause();
        mCameraView.pauseCamera();
    }

    @Override
    public synchronized void onStop() {
        LOGGER.d("onStop " + this);
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        LOGGER.d("onDestroy " + this);

        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                initialise();
            } else {
                requestPermission();
            }
        }
    }

    @Override
    public void onCameraStarted(Size previewSize, Size pictureSize) {
        LOGGER.d("onCameraStarted");
        // Allocate bitmap and mat.
        int width = previewSize.getHeight();
        int height = previewSize.getWidth();
        float scale = INFERENCE_SIZE / (float)Math.max(width, height);
        mInferenceWidth = (int)(width * scale);
        mInferenceHeight = (int)(height * scale);

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mMat = new Mat(height, width, CvType.CV_8UC3);
        mInferenceMat = new Mat(mInferenceHeight, mInferenceWidth, CvType.CV_8UC3);

        width = pictureSize.getHeight();
        height = pictureSize.getWidth();
        scale = PICTURE_INFERENCE_SIZE / (float) Math.max(width, height);
        mPictureInferenceWidth = (int) (width * scale);
        mPictureInferenceHeight = (int) (height * scale);

        Size preferredSize = ImageManager.getPreferredImageSize();
        scale = Math.max(preferredSize.getHeight(), preferredSize.getWidth())
                / (float)Math.max(width, height);

        mDisplayWidth = Math.round(scale * width);
        mDisplayHeight = Math.round(scale * height);
    }

    @Override
    public Bitmap processPreview(CameraViewBase.CameraFrame frame) {
        mMat = frame.rgb();

        if (mPausePreviewProcessing) {
            Utils.matToBitmap(mMat, mBitmap);
            return mBitmap;
        }

        Imgproc.resize(mMat, mInferenceMat, new org.opencv.core.Size(mInferenceWidth, mInferenceHeight));

        int length = (int)(mInferenceMat.total() * mInferenceMat.channels());
        byte matData[] = new byte[length];
        mInferenceMat.get(0, 0, matData);

        final List<Classifier.Recognition> results =
                mDetector.recognizeImage(matData, mInferenceHeight, mInferenceWidth);

        Classifier.Recognition result = results.get(0);
        ImageUtils.applyMask(
                mMat,
                mBitmap,
                result.getMask(),
                mInferenceWidth,
                mInferenceHeight,
                mPreviewBlurAmount,
                mGrayScale);

        return mBitmap;
    }

    @Override
    public void processPicture(final CameraViewBase.CameraFrame frame) {
        LOGGER.d("processPicture");
        mPausePreviewProcessing = true;

        runInBackground(new Runnable() {
            @Override
            public void run() {
                int width = frame.Height();
                int height = frame.Width();
                int infWidth = mPictureInferenceWidth;
                int infHeight = mPictureInferenceHeight;
                int disWidth = mDisplayWidth;
                int disHeight = mDisplayHeight;

                Bitmap pictureBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Bitmap displayBitmap = Bitmap.createBitmap(disWidth, disHeight, Bitmap.Config.ARGB_8888);
                Mat displayMat = new Mat(disHeight, disWidth, CvType.CV_8UC3);
                Mat inferenceMat = new Mat(infHeight, infWidth, CvType.CV_8UC3);

                Mat mat = frame.rgb();
                Utils.matToBitmap(mat, pictureBitmap);

                Imgproc.resize(mat, inferenceMat, new org.opencv.core.Size(infWidth, infHeight));
                Imgproc.resize(mat, displayMat, new org.opencv.core.Size(disWidth, disHeight));

                byte data[] = new byte[(int)inferenceMat.total() * inferenceMat.channels()];
                inferenceMat.get(0, 0, data);

                final List<Classifier.Recognition> results =
                        mDetector.recognizeImage(data, infHeight, infWidth);

                mPausePreviewProcessing = false;

                Classifier.Recognition result = results.get(0);
                int[] mask = result.getMask();

                ImageUtils.applyMask(displayMat,
                        displayBitmap,
                        mask,
                        infWidth,
                        infHeight,
                        mBlurAmount,
                        mGrayScale);

                ImageManager.getInstance().cacheBitmap(mFilename, displayBitmap);
                ImageData imageData = new ImageData(pictureBitmap, mask, infWidth, infHeight, mBlurAmount, mGrayScale);
                ImageManager.getInstance().storeImageData(mFilename, imageData);

                onProcessingComplete(mFilename);

                int blurAmount = Math.round((float)mBlurAmount * width / disWidth);
                Bitmap finalResult = Bitmap.createBitmap(pictureBitmap);
                ImageUtils.applyMask(mat, finalResult, mask, infWidth, infHeight, blurAmount, mGrayScale);
                ImageManager.getInstance().saveBitmap(mFilename, finalResult);

                finalResult.recycle();
                inferenceMat.release();
                displayMat.release();
                mat.release();
            }
        });
    }

    private void onProcessingComplete(final String filename) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LOGGER.d("onProcessingComplete");
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
                LOGGER.d("Found fragment = " + (fragment != null));

                if (fragment != null && fragment.isAdded()) {
                    if (fragment instanceof RecyclerViewFragment) {
                        ((RecyclerViewFragment)fragment).notifyImageChange(filename);
                    } else if (fragment instanceof GalleryViewPagerFragment) {
                        ((GalleryViewPagerFragment)fragment).notifyImageChange(filename);
                    }
                }
            }
        });
    }

    private void initialise() {
        LOGGER.d("Initialising camera.");
        mCameraView = findViewById(R.id.preview_activity_surface_view);
        mCameraView.setVisibility(View.VISIBLE);
        mCameraView.setProcessPreviewListener(this);
        mCameraView.setDesiredSize(DESIRED_PREVIEW_SIZE);

        try {
            if (mDetector == null) {
                LOGGER.d("Initialising detector.");
                mDetector = SegmentationModel.Create(
                        getAssets(),
                        SHUFFLESEG_MODEL_FILE,
                        "",
                        SHUFFLESEG_INPUT_NAME,
                        SHUFFLESEG_OUTPUT_NAMES);
            }
        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            finish();
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
                    shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(this,
                        "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[] {PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (mHandler != null) {
            mHandler.post(r);
        }
    }
}
