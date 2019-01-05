package com.lun.chin.aicamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
    private Context mContext;
    private ArrayList<ImageItem> mGalleryList;
    private final GalleryItemClickListener mGalleryItemClickListener;

    private static int displaySize = 200;

    public RecyclerViewAdapter(Context context,
                               ArrayList<ImageItem> galleryList,
                               GalleryItemClickListener galleryItemClickListener) {
        mContext = context;
        mGalleryList = galleryList;
        mGalleryItemClickListener = galleryItemClickListener;
    }

    @Override
    public RecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater
                        .from(viewGroup.getContext())
                        .inflate(R.layout.cell_layout, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final RecyclerViewAdapter.ViewHolder viewHolder, int i) {
        final ImageItem imageItem = mGalleryList.get(i);

        viewHolder.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // Load a smaller version of the image for displaying.
        Bitmap bitmap =
                ImageManager.getInstance().getSmallBitmap(imageItem, displaySize, displaySize);

        // Image is still processing. Display a black image as a placeholder.
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(displaySize, displaySize, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.BLACK);
        }

        viewHolder.imageView.setImageBitmap(bitmap);

        viewHolder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGalleryItemClickListener.onGalleryItemClickListener(
                        viewHolder.getAdapterPosition(), imageItem, viewHolder.imageView);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mGalleryList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        private ImageView imageView;
        public ViewHolder(View view) {
            super(view);
            imageView = (ImageView)view.findViewById(R.id.img);
        }
    }
}