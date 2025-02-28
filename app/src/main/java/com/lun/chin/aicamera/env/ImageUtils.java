package com.lun.chin.aicamera.env;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Environment;

import com.lun.chin.aicamera.ImageManager;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Utility class for manipulating images.
 **/
public class ImageUtils {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = new Logger();

    static {
        try {
            System.loadLibrary("tensorflow_demo");
        } catch (UnsatisfiedLinkError e) {
            LOGGER.w("Native library not found, native RGB -> YUV conversion may be unavailable.");
        }
    }

    /**
     * Utility method to compute the allocated size in bytes of a YUV420SP image
     * of the given dimensions.
     */
    public static int getYUVByteSize(final int width, final int height) {
        // The luminance plane requires 1 byte per pixel.
        final int ySize = width * height;

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        final int uvSize = ((width + 1) / 2) * ((height + 1) / 2) * 2;

        return ySize + uvSize;
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     */
    public static void saveBitmap(final Bitmap bitmap) {
        saveBitmap(bitmap, ImageManager.SAVE_PATH, "preview.png");
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     * @param root The location to save the bitmap to.
     * @param filename The name of the file.
     */
    public static void saveBitmap(final Bitmap bitmap, final String root, final String filename) {
        LOGGER.i("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), root);
        final File myDir = new File(root);

        if (!myDir.mkdirs()) {
            LOGGER.i("Make dir failed");
        }

        final String fname = filename;
        final File file = new File(myDir, fname);
        if (file.exists()) {
            file.delete();
        }
        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 99, out);
            out.flush();
            out.close();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
        }
    }

    public static void deleteBitmap(final String path) {
        final File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    static final int kMaxChannelValue = 262143;

    // Always prefer the native implementation if available.
    private static boolean useNativeConversion = true;

    public static void convertYUV420SPToARGB8888(
            byte[] input,
            int width,
            int height,
            int[] output) {
        if (useNativeConversion) {
            try {
                ImageUtils.convertYUV420SPToARGB8888(input, output, width, height, false);
                return;
            } catch (UnsatisfiedLinkError e) {
                LOGGER.w(
                        "Native YUV420SP -> RGB implementation not found, falling back to Java implementation");
                useNativeConversion = false;
            }
        }

        // Java implementation of YUV420SP to ARGB8888 converting
        final int frameSize = width * height;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width;
            int u = 0;
            int v = 0;

            for (int i = 0; i < width; i++, yp++) {
                int y = 0xff & input[yp];
                if ((i & 1) == 0) {
                    v = 0xff & input[uvp++];
                    u = 0xff & input[uvp++];
                }

                output[yp] = YUV2RGB(y, u, v);
            }
        }
    }

    private static int YUV2RGB(int y, int u, int v) {
        // Adjust and check YUV values
        y = (y - 16) < 0 ? 0 : (y - 16);
        u -= 128;
        v -= 128;

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);
        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
        g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
        b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }


    public static void convertYUV420ToARGB8888(
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            int[] out) {
        if (useNativeConversion) {
            try {
                convertYUV420ToARGB8888(
                        yData, uData, vData, out, width, height, yRowStride, uvRowStride, uvPixelStride, false);
                return;
            } catch (UnsatisfiedLinkError e) {
                LOGGER.w(
                        "Native YUV420 -> RGB implementation not found, falling back to Java implementation");
                useNativeConversion = false;
            }
        }

        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                out[yp++] = YUV2RGB(
                        0xff & yData[pY + i],
                        0xff & uData[uv_offset],
                        0xff & vData[uv_offset]);
            }
        }
    }


    /**
     * Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width and height. The
     * input and output must already be allocated and non-null. For efficiency, no error checking is
     * performed.
     *
     * @param input The array of YUV 4:2:0 input data.
     * @param output A pre-allocated array for the ARGB 8:8:8:8 output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     * @param halfSize If true, downsample to 50% in each dimension, otherwise not.
     */
    private static native void convertYUV420SPToARGB8888(
            byte[] input, int[] output, int width, int height, boolean halfSize);

    /**
     * Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width
     * and height. The input and output must already be allocated and non-null.
     * For efficiency, no error checking is performed.
     *
     * @param y
     * @param u
     * @param v
     * @param uvPixelStride
     * @param width The width of the input image.
     * @param height The height of the input image.
     * @param halfSize If true, downsample to 50% in each dimension, otherwise not.
     * @param output A pre-allocated array for the ARGB 8:8:8:8 output data.
     */
    private static native void convertYUV420ToARGB8888(
            byte[] y,
            byte[] u,
            byte[] v,
            int[] output,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            boolean halfSize);

    /**
     * Converts YUV420 semi-planar data to RGB 565 data using the supplied width
     * and height. The input and output must already be allocated and non-null.
     * For efficiency, no error checking is performed.
     *
     * @param input The array of YUV 4:2:0 input data.
     * @param output A pre-allocated array for the RGB 5:6:5 output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     */
    private static native void convertYUV420SPToRGB565(
            byte[] input, byte[] output, int width, int height);

    /**
     * Converts 32-bit ARGB8888 image data to YUV420SP data.  This is useful, for
     * instance, in creating data to feed the classes that rely on raw camera
     * preview frames.
     *
     * @param input An array of input pixels in ARGB8888 format.
     * @param output A pre-allocated array for the YUV420SP output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     */
    private static native void convertARGB8888ToYUV420SP(
            int[] input, byte[] output, int width, int height);

    /**
     * Converts 16-bit RGB565 image data to YUV420SP data.  This is useful, for
     * instance, in creating data to feed the classes that rely on raw camera
     * preview frames.
     *
     * @param input An array of input pixels in RGB565 format.
     * @param output A pre-allocated array for the YUV420SP output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     */
    private static native void convertRGB565ToYUV420SP(
            byte[] input, byte[] output, int width, int height);

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth Width of source frame.
     * @param srcHeight Height of source frame.
     * @param dstWidth Width of destination frame.
     * @param dstHeight Height of destination frame.
     * @param applyRotation Amount of rotation to apply from one frame to another.
     *  Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     * cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    public static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                            int reqWidth,
                                            int reqHeight) {

        // Raw height and width of image.
        final int height = options.outHeight;
        final int width = options.outWidth;

        return calculateInSampleSize(width, height, reqWidth, reqHeight);
    }

    private static int calculateInSampleSize(int width,
                                            int height,
                                            int reqWidth,
                                            int reqHeight) {

        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight &&
                    (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Create a bitmap of the specified width and height from an image file.
     *
     * @param filepath Path to the image file.
     * @param reqWidth The desired width of the resulting bitmap.
     * @param reqHeight The desired height of the resulting bitmap.
     * @return A bitmap representing the image file.
     */
    public static Bitmap decodeSampledBitmapFromFile(String filepath,
                                                     int reqWidth,
                                                     int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filepath, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filepath, options);
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res,
                                                         int resId,
                                                         int reqWidth,
                                                         int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static Bitmap resizeBitmapProportionally(Bitmap src, int reqWidth, int reqHeight) {
        int width = src.getWidth();
        int height = src.getHeight();
        int inSampleSize = calculateInSampleSize(width, height, reqWidth, reqHeight);
        int newWidth = width / inSampleSize;
        int newHeight = height / inSampleSize;

        return Bitmap.createScaledBitmap(src, newWidth, newHeight, true);
    }

    private static native void bokeh(
            long imgAddr, long maskAddr, long resultAddr, int previewWidth, int previewHeight, int blurAmount, boolean grayscale);

    public static void applyMask(Bitmap src, Bitmap dst, int[] mask, int maskWidth, int maskHeight, int blurAmount, boolean grayscale) {
        Mat img = new Mat();
        Utils.bitmapToMat(src, img);

        applyMask(img, dst, mask, maskWidth, maskHeight, blurAmount, grayscale);
    }

    /**
     * Calls native method to blur the background according to the mask data.
     *
     * The mask may not be of the same size as the source image. The native method will resize the
     * mask so that they are equal.
     *
     * @param src An OpenCV Mat representing the original image.
     * @param dst Bitmap to store the result.
     * @param mask Int array indicating the pixel location of the background and foreground of the source image.
     * @param maskWidth The width of the mask.
     * @param maskHeight The height of the mask.
     * @param blurAmount The intensity of blur to apply. Larger equals blurrier.
     * @param grayscale Whether to turn the background to black and white or not.
     */
    public static void applyMask(Mat src, Bitmap dst, int[] mask, int maskWidth, int maskHeight, int blurAmount, boolean grayscale) {
        final int w = src.width();
        final int h = src.height();

        Mat maskMat = new Mat(maskHeight, maskWidth, CvType.CV_32SC1);
        Mat outImage = new Mat(src.size(), src.type());
        maskMat.put(0, 0, mask);

        // Add bokeh effect.
        bokeh(src.getNativeObjAddr(),
                maskMat.getNativeObjAddr(),
                outImage.getNativeObjAddr(),
                w,
                h,
                blurAmount,
                grayscale);

        Utils.matToBitmap(outImage, dst);
    }
}
