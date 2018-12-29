package com.lun.chin.aicamera;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;

import com.lun.chin.aicamera.env.Logger;

import org.tensorflow.Graph;
import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;

public class DeepLab implements Classifier {
    private static final Logger LOGGER = new Logger();

    private final String INPUT_NAME = "ImageTensor";
    private final String[] OUTPUT_NAMES = { "SemanticPredictions" };

    private String[] mLabels = {
        "background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus",
        "car", "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike",
        "person", "pottedplant", "sheep", "sofa", "train", "tv"
    };

    private TensorFlowInferenceInterface mInferenceInterface;

    public static Classifier Create(final AssetManager assetManager,
                                    final String modelFilename,
                                    final String labelFilename) throws IOException {

        final DeepLab d = new DeepLab();

        /*
        InputStream labelsInput = null;
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        labelsInput = assetManager.open(actualFilename);
        BufferedReader br = null;
        br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            d.mLabels.add(line);
        }
        br.close();
        */

        d.mInferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);
        final Graph g = d.mInferenceInterface.graph();

        // The INPUT_NAME node has a shape of [N, H, W, C], where
        // N is the batch size
        // H = W are the height and width
        // C is the number of channels (3 for our purposes - RGB)
        final Operation inputOp = g.operation(d.INPUT_NAME);
        if (inputOp == null) {
            throw new RuntimeException("Failed to find input Node '" + d.INPUT_NAME + "'");
        }

        for (String name : d.OUTPUT_NAMES) {
            final Operation outputOp = g.operation(name);
            if (outputOp == null) {
                throw new RuntimeException("Failed to find output Node '" + name + "'");
            }
        }

        return d;
    }

    @Override
    public List<Recognition> recognizeImage(final Bitmap bitmap) {
        int inputWidth = bitmap.getWidth();
        int inputHeight = bitmap.getHeight();
        int[] intValues = new int[inputWidth * inputHeight];
        byte[] byteValues = new byte[inputWidth * inputHeight * 3];
        bitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        for (int i = 0; i < intValues.length; ++i) {
            byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF);
            byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF);
            byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF);
        }

        //mInferenceInterface.feed(INPUT_NAME, byteValues, 1, inputWidth, inputHeight, 3);
        mInferenceInterface.feed(INPUT_NAME, byteValues, 1, inputHeight, inputWidth, 3);
        mInferenceInterface.run(OUTPUT_NAMES, false);

        // outputSegMap is an array where each element is an integer corresponding to the class label.
        int[] outputSegMap = new int[inputWidth * inputHeight];
        mInferenceInterface.fetch(OUTPUT_NAMES[0], outputSegMap);

        // We don't care about the labels; just set it to "".
        Recognition recognition = new Recognition(
                "segmentation", "", 1f, null, outputSegMap);

        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        recognitions.add(recognition);

        return recognitions;
    }

    @Override
    public void enableStatLogging(final boolean logStats) {}

    @Override
    public String getStatString() {
        return mInferenceInterface.getStatString();
    }

    @Override
    public void close() {
        mInferenceInterface.close();
    }
}
