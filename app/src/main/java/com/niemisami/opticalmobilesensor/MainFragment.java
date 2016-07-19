package com.niemisami.opticalmobilesensor;


import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.niemisami.opticalmobilesensor.utils.CameraHelper;
import com.niemisami.opticalmobilesensor.utils.ColorAverageFileWriter;
import com.niemisami.opticalmobilesensor.utils.ImageProcessing;
import com.niemisami.opticalmobilesensor.views.AutoFitTextureView;
import com.niemisami.opticalmobilesensor.views.LineGraphView;

import org.achartengine.GraphicalView;


/**
 * A simple {@link Fragment} subclass.
 */
public class MainFragment extends Fragment
        implements RadioGroup.OnCheckedChangeListener {

    private static final String TAG = MainFragment.class.getName();

    private int mWantedRGBColor = Color.RED;
    private LineGraphView mLineGraph;
    private GraphicalView mGraphView;

    private CameraHelper mCameraHelper;

    private ColorAverageFileWriter mColorWriter;
    private HandlerThread mFileBackgroundThread, mBackgroundImageProcessingThread;
    private Handler mFileBackgroundHandler, mBackgroundImageProsessingHandler;
    private AutoFitTextureView mTextureView;


    private double mCounter = 0.0;
    private int REFRESH_INTERVAL = 1000 / 24; // 24 fps
    private float AVERAGE_COLOR;
    private Handler mGraphUpdater = new Handler();
    private ViewGroup mGraphLayout;
    private TextView mGraphRefreshRateTextView;
    private ImageView mFlashIcon;

    private CameraHelper.OnFrameProcessedListener mOnFrameProcessedListener = new CameraHelper.OnFrameProcessedListener() {
        @Override
        public void onFrameArrayInt(int[] outBuffer, long timestamp) {

            mFileBackgroundHandler.post(createFileWriterRunnable(outBuffer, timestamp));
            float[] averageRGBColors = new float[3];
            ImageProcessing.averagingColorArrayToRGBChannels(outBuffer, averageRGBColors);
            switch (mWantedRGBColor) {
                case Color.RED:
                    AVERAGE_COLOR = averageRGBColors[0];
                    break;
                case Color.GREEN:
                    AVERAGE_COLOR = averageRGBColors[1];
                    break;
                case Color.BLUE:
                    AVERAGE_COLOR = averageRGBColors[2];
                    break;
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mTextureView = new AutoFitTextureView(getActivity());
//        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(128, 96);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(144, 144);
        mTextureView.setLayoutParams(layoutParams);
        final RelativeLayout layout = (RelativeLayout) rootView.findViewById(R.id.fragment_decoder_layout);
        mCameraHelper = new CameraHelper(getActivity(), mTextureView, mOnFrameProcessedListener);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (layout.getChildAt(0) == mTextureView) return;
                layout.addView(mTextureView, 0);
                startBackgroundThreads();
            }
        }, 700);

        initFileWriter();
        initGraph(rootView);
        initRadioButtons(rootView);
        initSeekBar(rootView);
        initFlashLightView(rootView);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).

        if (mTextureView.isAvailable()) {
            mCameraHelper.openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        mCameraHelper.closeCamera();
        mGraphUpdater.removeCallbacks(mRepeatTask);
        stopBackgroundThread();
        mColorWriter.close();
        super.onPause();
    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    mCameraHelper.openCamera(width, height);
                    mRepeatTask.run();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                    mCameraHelper.configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                }

            };


    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThreads() {
        mFileBackgroundThread = new HandlerThread("FileWriterBackground");
        mFileBackgroundThread.start();
        mFileBackgroundHandler = new Handler(mFileBackgroundThread.getLooper());

        mBackgroundImageProcessingThread = new HandlerThread("ImageProcessingBackground");
        mBackgroundImageProcessingThread.start();
        mBackgroundImageProsessingHandler = new Handler(mBackgroundImageProcessingThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        try {
            mFileBackgroundHandler.removeCallbacksAndMessages(null);
            mFileBackgroundThread.quit();
            mFileBackgroundThread.join();
            mFileBackgroundThread = null;
            mFileBackgroundHandler = null;

            mBackgroundImageProsessingHandler.removeCallbacksAndMessages(null);
            mBackgroundImageProcessingThread.quit();
            mBackgroundImageProcessingThread.join();
            mBackgroundImageProcessingThread = null;
            mBackgroundImageProsessingHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread:", e);
            mFileBackgroundThread = null;
            mFileBackgroundHandler = null;
            mBackgroundImageProcessingThread = null;
            mBackgroundImageProsessingHandler = null;
        } catch (NullPointerException e) {
            Log.e(TAG, "stopBackgroundThread:", e);
            mFileBackgroundThread = null;
            mFileBackgroundHandler = null;
            mBackgroundImageProcessingThread = null;
            mBackgroundImageProsessingHandler = null;
        }
    }


    private void initGraph(View view) {
        mLineGraph = new LineGraphView(mWantedRGBColor);
        mGraphView = mLineGraph.getView(getActivity());
        mGraphLayout = (ViewGroup) view.findViewById(R.id.graph_hrs);
        mGraphLayout.addView(mGraphView);
    }

    private void reinitGraph() {
        mGraphLayout.removeView(mGraphView);
        mLineGraph = new LineGraphView(mWantedRGBColor);
        mGraphView = mLineGraph.getView(getActivity());
        mGraphLayout.addView(mGraphView);
    }

    private void initSeekBar(View view) {
        mGraphRefreshRateTextView = (TextView) view.findViewById(R.id.graph_refresh_rate_progress);
        AppCompatSeekBar graphRefreshRateSeekBar = (AppCompatSeekBar) view.findViewById(R.id.graph_refresh_rate_seek_bar);
        graphRefreshRateSeekBar.setProgress(100);
        graphRefreshRateSeekBar.setOnSeekBarChangeListener(mOnRefreshRateSeekBarChangeListener);
    }

    private void initRadioButtons(View view) {
        RadioGroup RGBRadioGroup = (RadioGroup) view.findViewById(R.id.radio_group_rgb);
        RGBRadioGroup.setOnCheckedChangeListener(this);
    }

    private void initFlashLightView(View view) {
        mFlashIcon = (ImageView) view.findViewById(R.id.flash_icon);
        mFlashIcon.setImageLevel(1);

        SwitchCompat mFlashSwitch = (SwitchCompat) view.findViewById(R.id.flash_toggle_switch);
        mFlashSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "onCheckedChanged: ");
                if (isChecked) {
                    mFlashIcon.setImageLevel(1);
                } else {
                    mFlashIcon.setImageLevel(0);
                }
                mCameraHelper.toggleFlash(isChecked);

            }
        });
    }

    private void updateGraph(final float yValue) {
        mCounter++;
        mLineGraph.addValue(mCounter, yValue);
        mGraphView.repaint();
    }

    private Runnable mRepeatTask = new Runnable() {
        @Override
        public void run() {
            updateGraph(AVERAGE_COLOR);
            mGraphUpdater.postDelayed(mRepeatTask, REFRESH_INTERVAL);
        }
    };

    private Runnable createFileWriterRunnable(final int[] colorArray, final long timestamp) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                float[] averageRGBColors = new float[3];
                ImageProcessing.averagingColorArrayToRGBChannels(colorArray, averageRGBColors);
                mColorWriter.writeArray(averageRGBColors, timestamp);
            }
        };
        return task;
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        boolean checked = ((RadioButton) group.findViewById(checkedId)).isChecked();
        // Check which radio button was clicked
        switch (checkedId) {
            case R.id.radio_red:
                Log.d(TAG, "onCheckedChanged: red");
                if (checked)
                    mWantedRGBColor = Color.RED;
                break;
            case R.id.radio_green:
                Log.d(TAG, "onCheckedChanged: green");
                if (checked)
                    mWantedRGBColor = Color.GREEN;
                break;
            case R.id.radio_blue:
                Log.d(TAG, "onCheckedChanged: blue");
                if (checked)
                    mWantedRGBColor = Color.BLUE;
                break;
        }
        reinitGraph();
    }

    public SeekBar.OnSeekBarChangeListener mOnRefreshRateSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && progress > 0) {
                long progressValue = Math.round(progress * 0.3);
                mGraphRefreshRateTextView.setText(Long.toString(progressValue));
            } else {
                mGraphRefreshRateTextView.setText("1");
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (seekBar.getProgress() > 0) {
                long progressValue = Math.round(seekBar.getProgress() * 0.3);
                REFRESH_INTERVAL = 1000 / (int) progressValue;
            } else {
                REFRESH_INTERVAL = 1000;
            }
        }
    };

    private void initFileWriter() {
        mColorWriter = new ColorAverageFileWriter(getActivity(), "rgb");
    }
}
