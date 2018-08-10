package com.example.chin.instancesegmentation;

import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;

public class ImageManager
{
    public static final String SAVE_DIR = "tensorflow";

    /**
     * List of identifiers of currently processing images.
     */
    private ArrayList<String> mPendingImages = new ArrayList<>();

    private ImageManager mInstance;

    public ImageManager getInstance() {
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
        File saveDir = Environment.getExternalStoragePublicDirectory(getSavePath());

        if (saveDir.isDirectory()) {
            File[] files = saveDir.listFiles();

            for (int i = 0; i < files.length; ++i) {
                ImageItem item = new ImageItem(files[i].getName(), files[i].getPath());
                images.add(item);
            }
        }

        for (int i = 0; i < mPendingImages.size(); ++i) {
            // Pending images don't have a file path.
            ImageItem item = new ImageItem(mPendingImages.get(i), null);
            images.add(item);
        }

        return images;
    }

    public Bitmap getBitmap(ImageItem imageItem) {

    }
}
