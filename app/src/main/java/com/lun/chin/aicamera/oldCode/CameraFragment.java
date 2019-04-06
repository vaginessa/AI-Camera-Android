package com.lun.chin.aicamera.oldCode;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.view.View;

public class CameraFragment extends Fragment {
    protected OnCameraButtonClickedListener mCameraButtonCallback;

    protected View.OnClickListener mOnCameraButtonClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mCameraButtonCallback.onCameraButtonClicked(v);
        }
    };

    public interface OnCameraButtonClickedListener {
        void onCameraButtonClicked(View v);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mCameraButtonCallback = (OnCameraButtonClickedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnCameraButtonClickedListener");
        }
    }
}
