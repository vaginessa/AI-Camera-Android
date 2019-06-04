package com.lun.chin.aicamera;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;

import com.lun.chin.aicamera.env.ImageUtils;
import com.lun.chin.aicamera.env.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class ImageManager
{
    private static final Logger LOGGER = new Logger();

     // R.string needs a Context which this class doesn't have.
     // For simplicity just hardcode the app name here.
    public static final String SAVE_PATH =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .getAbsolutePath()
                    + File.separator
                    + "AiCamera";

    /**
     * List of identifiers of currently processing images.
     */
    private ArrayList<String> mPendingImages = new ArrayList<>();
    private HashMap<String, Bitmap> mCachedBitmap = new HashMap<>();
    private HashMap<String, ImageData> mImageDataMap = new HashMap<>();

    private Size mPreferredImageSize;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private static ImageManager mInstance;

    public static ImageManager getInstance() {
        if (mInstance == null) {
            mInstance = new ImageManager();
            mInstance.mPreferredImageSize = new Size(
                    Resources.getSystem().getDisplayMetrics().widthPixels / 2,
                    Resources.getSystem().getDisplayMetrics().heightPixels / 2);

            mInstance.startBackgroundThread();
        }
        return mInstance;
    }

    public static Size getPreferredImageSize() {
        return getInstance().mPreferredImageSize;
    }

    /**
     * @return ArrayList of ImageItem representing the collection of images to show in the gallery.
     * The images may be saved on file or currently processing.
     */
    public ArrayList<ImageItem> getImageItems() {
        ArrayList<ImageItem> images = new ArrayList<>();
        File saveDir = new File(SAVE_PATH);

        for (String title : mCachedBitmap.keySet()) {
            ImageItem item = new ImageItem(title, SAVE_PATH + File.separator + title);
            images.add(item);
        }

        if (saveDir.isDirectory()) {
            File[] files = saveDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    ImageItem item = new ImageItem(file.getName(), file.getPath());
                    images.add(item);
                }
            }
        }

        if (images.size() > 1) {
            // Sort by the timestamp in the filename. Newest first.
            Collections.sort(images, new Comparator<ImageItem>() {
                @Override
                public int compare(ImageItem a, ImageItem b) {
                    return -1 * a.getTitle().compareTo(b.getTitle());
                }
            });
        }

        return images;
    }

    public Bitmap getBitmap(ImageItem imageItem) {
        Bitmap bitmap = BitmapFactory.decodeFile(imageItem.getPath());

        if (bitmap == null) {
            if (mCachedBitmap.containsKey(imageItem.getTitle())) {
                bitmap = mCachedBitmap.get(imageItem.getTitle());
            }
        }

        return bitmap;
    }

    public Bitmap getSmallBitmap(ImageItem imageItem, int width, int height) {
        Bitmap bitmap;
        if (mCachedBitmap.containsKey(imageItem.getTitle())) {
            bitmap = mCachedBitmap.get(imageItem.getTitle());
        } else {
            bitmap = ImageUtils.decodeSampledBitmapFromFile(
                        imageItem.getPath(),
                        width,
                        height);
        }

        return bitmap;
    }

    public void addPendingImage(String title) {
        mPendingImages.add(title);
        mCachedBitmap.put(title, null);
    }

    public void cacheBitmap( String title, Bitmap bitmap) {
        mCachedBitmap.put(title, bitmap);
    }

    public void storeImageData(String title, ImageData imageData) {
        mImageDataMap.put(title, imageData);
    }

    public void saveBitmap(String title, Bitmap bitmap) {
        // Use the title as the filename.
        ImageUtils.saveBitmap(bitmap, SAVE_PATH, title);
        mPendingImages.remove(title);
        mCachedBitmap.remove(title);
    }

    public void saveBitmapAsync(final String title, final Bitmap bitmap) {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                saveBitmap(title, bitmap);
            }
        });
    }

    public void deleteBitmap(ImageItem imageItem) {
        final String title = imageItem.getTitle();
        ImageUtils.deleteBitmap(imageItem.getPath());
        mPendingImages.remove(title);
        mCachedBitmap.remove(title);
        mImageDataMap.remove(title);
    }

    public boolean hasMaskData(ImageItem item) {
        return mImageDataMap.containsKey(item.getTitle());
    }

    public ImageData getImageData(String title) {
        return mImageDataMap.get(title);
    }

    public void quit() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("ImageManagerBG");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
}
