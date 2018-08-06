package com.example.chin.instancesegmentation;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {
    private File[] mFiles;
    private String[] mFilePaths;
    private String[] mFileNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.content, RecyclerViewFragment.newInstance(getImages()))
                .commit();
    }

    private ArrayList<ImageItem> getImages() {
        ArrayList<ImageItem> images = new ArrayList<>();

        final String root =
                Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";

        File dirDownload = Environment.getExternalStoragePublicDirectory(root);

        if (dirDownload.isDirectory()) {
            mFiles = dirDownload.listFiles();
            mFilePaths = new String[mFiles.length];
            mFileNames = new String[mFiles.length];

            for (int i = 0; i < mFiles.length; i++) {
                mFilePaths[i] = mFiles[i].getAbsolutePath();
                mFileNames[i] = mFiles[i].getName();
            }

            for (int i = 0; i < mFileNames.length; ++i) {
                ImageItem item = new ImageItem(mFileNames[i], mFilePaths[i]);
                images.add(item);
            }
        }

        return images;
    }
}
