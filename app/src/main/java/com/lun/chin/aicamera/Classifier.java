/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.lun.chin.aicamera;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;

/**
 * Generic interface for interacting with different recognition engines.
 */
public interface Classifier {
  /**
   * An immutable result returned by a Classifier describing what was recognized.
   */
  public class Recognition {
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of
     * the object.
     */
    private final String mId;

    /**
     * Display name for the recognition.
     */
    private final String mTitle;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    private final Float mConfidence;

    /** Optional mLocation within the source image for the mLocation of the recognized object. */
    private RectF mLocation;

    /** Optional mMask indicating the pixel mLocation of the object */
    private int[] mMask;

    public Recognition(final String mId,
                       final String title,
                       final Float confidence,
                       final RectF location,
                       final int[] mask) {
      this.mId = mId;
      this.mTitle = title;
      this.mConfidence = confidence;
      this.mLocation = location;
      this.mMask = mask;
    }

    public String getId() {
      return mId;
    }

    public String getTitle() {
      return mTitle;
    }

    public Float getConfidence() {
      return mConfidence;
    }

    public RectF getLocation() {
      return new RectF(mLocation);
    }

    public int[] getMask(){
      return mMask;
    }

    public void setLocation(RectF location) {
      this.mLocation = location;
    }

    @Override
    public String toString() {
      String resultString = "";
      if (mId != null) {
        resultString += "[" + mId + "] ";
      }

      if (mTitle != null) {
        resultString += mTitle + " ";
      }

      if (mConfidence != null) {
        resultString += String.format("(%.1f%%) ", mConfidence * 100.0f);
      }

      if (mLocation != null) {
        resultString += mLocation + " ";
      }

      return resultString.trim();
    }
  }

  List<Recognition> recognizeImage(Bitmap bitmap);

  void enableStatLogging(final boolean debug);

  String getStatString();

  void close();
}
