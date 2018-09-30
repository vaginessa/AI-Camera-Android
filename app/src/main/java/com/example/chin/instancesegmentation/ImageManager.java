package com.example.chin.instancesegmentation;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import com.example.chin.instancesegmentation.env.ImageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;

public class ImageManager
{
    public static final String SAVE_DIR = "tensorflow";

    /**
     * List of identifiers of currently processing images.
     */
    private ArrayList<String> mPendingImages = new ArrayList<>();
    private HashMap<String, Bitmap> mCachedBitmap = new HashMap<>();

    private static ImageManager mInstance;

    public static ImageManager getInstance() {
        if (mInstance == null) {
            mInstance = new ImageManager();
        }
        return mInstance;
    }

    public static String getSavePath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator
                + SAVE_DIR;
    }

    /**
     * @return ArrayList of ImageItem representing the collection of images to show in the gallery.
     * The images may be saved on file or currently processing.
     */
    public ArrayList<ImageItem> getImageItems() {
        ArrayList<ImageItem> images = new ArrayList<>();
        File saveDir = new File(getSavePath());

        for (String title : mCachedBitmap.keySet()) {
            ImageItem item = new ImageItem(title, null);
            images.add(item);
        }

        if (saveDir.isDirectory()) {
            File[] files = saveDir.listFiles();

            if (files != null) {
                if (files.length > 1) {
                    // Sort by the timestamp in the filename. Newest first.
                    Arrays.sort(files, new Comparator<File>() {
                        @Override
                        public int compare(File a, File b) {
                            return -1 * a.getName().compareTo(b.getName());
                        }
                    });

                    for (File file : files) {
                        ImageItem item = new ImageItem(file.getName(), file.getPath());
                        images.add(item);
                    }
                }
            }
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
        Bitmap bitmap =
                ImageUtils.decodeSampledBitmapFromFile(
                        imageItem.getPath(),
                        width,
                        height);

        // If image is not in local storage then try load from cache.
        if (bitmap == null) {
            if (mCachedBitmap.containsKey(imageItem.getTitle())) {
                bitmap = mCachedBitmap.get(imageItem.getTitle());
            }
        }

        return bitmap;
    }

    public void addPendingImage(String title) {
        mPendingImages.add(title);
        mCachedBitmap.put(title, null);
    }

    public void cacheBitmap(String title, Bitmap bitmap) {
        // Only cache a scaled down version of the bitmap.
        final int width = Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        final int height = Resources.getSystem().getDisplayMetrics().heightPixels / 2;
        Bitmap resizedBitmap = ImageUtils.resizeBitmapProportionally(bitmap, width, height);
        mCachedBitmap.put(title, resizedBitmap);
    }

    public void saveBitmap(String title, Bitmap bitmap) {
        // Use the title as the filename.
        ImageUtils.saveBitmap(bitmap, title);
        mPendingImages.remove(title);
    }

    public void clearCachedBitmap() {
        Set<String> titles = mCachedBitmap.keySet();
        for (String title : titles) {
            if (!mPendingImages.contains(title)) {
                mCachedBitmap.remove(title);
            }
        }
    }
}
