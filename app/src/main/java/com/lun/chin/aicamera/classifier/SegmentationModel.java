package com.lun.chin.aicamera.classifier;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.Graph;
import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SegmentationModel implements Classifier {

    private String mInputTensorName;
    private String[] mOutputTensorNames;

    // Use the VOC Pascal dataset labels as default.
    private String[] mLabels = {
            "background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus",
            "car", "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike",
            "person", "pottedplant", "sheep", "sofa", "train", "tv"
    };

    private TensorFlowInferenceInterface mInferenceInterface;

    public static Classifier Create(final AssetManager assetManager,
                                    final String modelFilename,
                                    final String labelFilename,
                                    final String inputTensorName,
                                    final String[] outputTensorNames) throws IOException, RuntimeException {

        final SegmentationModel d = new SegmentationModel();

        d.mInputTensorName = inputTensorName;
        d.mOutputTensorNames = outputTensorNames;

        d.mInferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);
        final Graph g = d.mInferenceInterface.graph();

        // The inputTensorName node has a shape of [N, H, W, C], where
        // N is the batch size,
        // H and W are the height and width,
        // C is the number of channels.
        final Operation inputOp = g.operation(d.mInputTensorName);
        if (inputOp == null) {
            throw new RuntimeException("Failed to find input Node '" + d.mInputTensorName + "'");
        }

        for (String name : d.mOutputTensorNames) {
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

        return recognizeImage(byteValues, inputHeight, inputWidth);
    }

    @Override
    public List<Recognition> recognizeImage(final byte[] data, int inputHeight, int inputWidth) {
        mInferenceInterface.feed(mInputTensorName, data, 1, inputHeight, inputWidth, 3);
        mInferenceInterface.run(mOutputTensorNames, false);

        // outputSegMap is an array where each element is an integer corresponding to the class label.
        int[] outputSegMap = new int[inputWidth * inputHeight];
        mInferenceInterface.fetch(mOutputTensorNames[0], outputSegMap);

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
