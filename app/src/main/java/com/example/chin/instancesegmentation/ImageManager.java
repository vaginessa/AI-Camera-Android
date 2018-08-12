package com.example.chin.instancesegmentation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import com.example.chin.instancesegmentation.env.ImageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

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

        if (saveDir.isDirectory()) {
            File[] files = saveDir.listFiles();

            for (int i = 0; i < files.length; ++i) {
                ImageItem item = new ImageItem(files[i].getName(), files[i].getPath());
                images.add(item);
            }
        }

        for (String title : mCachedBitmap.keySet()) {
            ImageItem item = new ImageItem(title, null);
            images.add(item);
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
                if (bitmap != null) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                }
            }
        }

        return bitmap;
    }

    public void addPendingImage(String title) {
        mCachedBitmap.put(title, null);
    }

    public void cacheBitmap(String title, Bitmap bitmap) {
        mCachedBitmap.put(title, bitmap);
    }

    public void saveBitmap(String title, Bitmap bitmap) {
        // Use the title as the filename.
        ImageUtils.saveBitmap(bitmap, title);
        mCachedBitmap.remove(title);
    }
}
