package com.example.chin.instancesegmentation;

import android.graphics.Bitmap;

public class ImageData
{
    private Bitmap mOriginalImage;
    private int[] mMask;
    private int mMaskWidth;
    private int mMaskHeight;
    private int mBlurAmount;
    private boolean mGrayscale;

    public ImageData(
            Bitmap originalImage, int[] mask, int maskWidth, int maskHeight, int blurAmount, boolean grayscale) {

        mOriginalImage = originalImage;
        mMask = mask;
        mMaskWidth = maskWidth;
        mMaskHeight = maskHeight;
        mBlurAmount = blurAmount;
        mGrayscale = grayscale;
    }

    public Bitmap getOriginalImage()
    {
        return mOriginalImage;
    }

    public void setOriginalImage(Bitmap mOriginalImage)
    {
        this.mOriginalImage = mOriginalImage;
    }

    public int[] getMask()
    {
        return mMask;
    }

    public void setMask(int[] mMask)
    {
        this.mMask = mMask;
    }

    public int getMaskWidth()
    {
        return mMaskWidth;
    }

    public void setMaskWidth(int mMaskWidth)
    {
        this.mMaskWidth = mMaskWidth;
    }

    public int getMaskHeight()
    {
        return mMaskHeight;
    }

    public void setMaskHeight(int mMaskHeight)
    {
        this.mMaskHeight = mMaskHeight;
    }

    public int getBlurAmount()
    {
        return mBlurAmount;
    }

    public void setBlurAmount(int mBlurAmount)
    {
        this.mBlurAmount = mBlurAmount;
    }

    public boolean isGrayscale()
    {
        return mGrayscale;
    }

    public void setGrayscale(boolean mGrayscale)
    {
        this.mGrayscale = mGrayscale;
    }
}
