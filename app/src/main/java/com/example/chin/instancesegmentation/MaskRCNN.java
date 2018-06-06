package com.example.chin.instancesegmentation;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;

import com.example.chin.instancesegmentation.env.Logger;

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

public class MaskRCNN implements Classifier {
    private static final Logger LOGGER = new Logger();

    // Only return this many results.
    private static final int MAX_RESULTS = 10;

    // Config values.
    private String mInputName;
    private String[] mOutputNames;

    // Pre-allocated buffers.
    private Vector<String> mLabels = new Vector<String>();

    private TensorFlowInferenceInterface mInferenceInterface;

    private MaskRCNN() {}

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     */
    public static Classifier create(final AssetManager assetManager,
                                    final String modelFilename,
                                    final String labelFilename) throws IOException {

        final MaskRCNN d = new MaskRCNN();

        InputStream labelsInput = null;
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        labelsInput = assetManager.open(actualFilename);
        BufferedReader br = null;
        br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            LOGGER.w(line);
            d.mLabels.add(line);
        }
        br.close();

        d.mInferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);
        d.mInputName = "image_tensor";
        d.mOutputNames = new String[] { "detection_classes", "detection_scores",
                                       "output_mask_reframed", "detection_boxes" };

        final Graph g = d.mInferenceInterface.graph();

        // The mInputName node has a shape of [N, H, W, C], where
        // N is the batch size
        // H = W are the height and width
        // C is the number of channels (3 for our purposes - RGB)
        final Operation inputOp = g.operation(d.mInputName);
        if (inputOp == null) {
            throw new RuntimeException("Failed to find input Node '" + d.mInputName + "'");
        }

        for (String name : d.mOutputNames) {
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

        mInferenceInterface.feed(mInputName, byteValues, 1, inputWidth, inputHeight, 3);
        mInferenceInterface.run(mOutputNames, false);

        float[] outputScores = new float[MAX_RESULTS];
        float[] outputClasses = new float[MAX_RESULTS];
        float[] outputLocations = new float[MAX_RESULTS * 4];
        int[] outputMask = new int[inputWidth * inputHeight * MAX_RESULTS];
        mInferenceInterface.fetch(mOutputNames[0], outputClasses);
        mInferenceInterface.fetch(mOutputNames[1], outputScores);
        mInferenceInterface.fetch(mOutputNames[2], outputMask);
        mInferenceInterface.fetch(mOutputNames[3], outputLocations);

        // Find the best detections.
        final PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(
                        1,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(final Recognition lhs, final Recognition rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        int size = inputHeight;
        for (int i = 0; i < outputScores.length; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[4 * i + 1] * size,
                            outputLocations[4 * i] * size,
                            outputLocations[4 * i + 3] * size,
                            outputLocations[4 * i + 2] * size);

            int[] mask = Arrays.copyOfRange(outputMask,
                    i * inputWidth * inputHeight,
                    (i + 1) * inputWidth * inputHeight);

            pq.add(
                    new Recognition(
                            "" + i, mLabels.get((int)outputClasses[i]), outputScores[i], detection, mask));
        }

        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        for (int i = 0; i < Math.min(pq.size(), MAX_RESULTS); ++i) {
            recognitions.add(pq.poll());
        }

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
