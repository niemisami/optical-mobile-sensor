package com.niemisami.opticalmobilesensor;


import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.ImageReader;
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

    public static boolean accessGranted = true;
    private ColorAverageFileWriter mColorWriter;
    private HandlerThread mFileBackgroundThread, mBackgroundImageProcessingThread;
    private Handler mFileBackgroundHandler, mBackgroundImageProsessingHandler;

    private AutoFitTextureView mTextureView;
    private CameraHelper.OnFrameProcessedListener mOnFrameProcessedListener = new CameraHelper.OnFrameProcessedListener() {
        @Override
        public void onFrameArrayInt(int[] outBuffer, long timestamp) {

            mFileBackgroundHandler.post(createFileWriterRunnable(outBuffer, timestamp));
            float averageColorSum = 0.0f;
            for (int color : outBuffer) {
                switch (color) {
                    case Color.RED:
                        averageColorSum += Color.red(color);
                        break;
                    case Color.GREEN:
                        averageColorSum += Color.green(color);
                        break;
                    case Color.BLUE:
                        averageColorSum += Color.blue(color);
                        break;
                }
            }

            AVERAGE_COLOR = averageColorSum / outBuffer.length;
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


    private double mCounter = 0.0;
    private int REFRESH_INTERVAL = 100; // 1 second interval
    private float AVERAGE_COLOR;
    private Handler mGraphUpdater = new Handler();

    private ViewGroup mGraphLayout;

    private TextView mScannableAreaTextView, mGraphRefreshRateTextView;
    private int SCANNABLE_AREA = 40;

    private ImageView mFlashIcon;
    private SwitchCompat mFlashSwitch;


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
        mScannableAreaTextView = (TextView) view.findViewById(R.id.seek_bar_progress);
        AppCompatSeekBar scanAreaSeekBar = (AppCompatSeekBar) view.findViewById(R.id.image_scan_size_seekbar);
        scanAreaSeekBar.setOnSeekBarChangeListener(mOnScanAreaSeekBarChangeListener);

        mGraphRefreshRateTextView = (TextView) view.findViewById(R.id.graph_refresh_rate_progress);
        AppCompatSeekBar graphRefreshRateSeekBar = (AppCompatSeekBar) view.findViewById(R.id.graph_refresh_rate_seek_bar);
        graphRefreshRateSeekBar.setOnSeekBarChangeListener(mOnRefreshRateSeekBarChangeListener);
    }

    private void initRadioButtons(View view) {
        RadioGroup RGBRadioGroup = (RadioGroup) view.findViewById(R.id.radio_group_rgb);
        RGBRadioGroup.setOnCheckedChangeListener(this);
    }

    private void initFlashLightView(View view) {
        mFlashIcon = (ImageView) view.findViewById(R.id.flash_icon);
        mFlashIcon.setImageLevel(1);

        mFlashSwitch = (SwitchCompat) view.findViewById(R.id.flash_toggle_switch);
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

    private Runnable mFileWriteTask = new Runnable() {

        @Override
        public void run() {
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

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {
//                    Log.e(TAG, "onImageAvailable: " + count++);
                    Image img = reader.acquireLatestImage();
                    try {
                        if (img == null) throw new NullPointerException("cannot be null");
                        AVERAGE_COLOR = ImageProcessing.calculateAverageColor(mWantedRGBColor, img, reader.getHeight(), reader.getWidth());
                        mFileBackgroundHandler.post(mFileWriteTask);
                    } catch (NullPointerException e) {
                        Log.e(TAG, "onImageAvailable: ", e);
                    } finally {
                        if (img != null)
                            img.close();
                    }
                }
            };

    private class ImageProcessor implements Runnable {
        private final Image mImage;

        public ImageProcessor(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            sendImageToProcessing(mImage);
        }
    }

    private void sendImageToProcessing(Image img) {
        long start = System.currentTimeMillis();
        try {

            RectF yuvDimens = new RectF(0, 0, img.getWidth(),
                    img.getHeight());
            final int PATCH_DIMEN = SCANNABLE_AREA; // pixels in YUV
            // Find matching square patch of pixels in YUV and JPEG output
            RectF tempPatch = new RectF(0, 0, PATCH_DIMEN, PATCH_DIMEN);
            tempPatch.offset(yuvDimens.centerX() - tempPatch.centerX(),
                    yuvDimens.centerY() - tempPatch.centerY());
            Rect yuvPatch = new Rect();
            tempPatch.roundOut(yuvPatch);

//                        AVERAGE_COLOR = ImageProcessing.calculateAverageOfColor(mWantedRGBColor, yuvPatch.width(),
//                                yuvPatch.height(), yuvPatch.left, yuvPatch.top, img);
            AVERAGE_COLOR = ImageProcessing.calculateAverageHueValueFromImage(yuvPatch.width(),
                    yuvPatch.height(), yuvPatch.left, yuvPatch.top, img);


//            //////TESTING DIFFERENT IMAGE FORMAT (PixelFormat.RGBA_8888)//////
//
//            Bitmap bitmap = null;
//            try {
//
//                if (img != null) {
//                    Image.Plane[] planes = img.getPlanes();
//                    if (planes[0].getBuffer() == null) {
//                        return;
//                    }
//                    int width = img.getWidth();
//                    int height = img.getHeight();
//                    int pixelStride = planes[0].getPixelStride();
//                    int rowStride = planes[0].getRowStride();
//                    int rowPadding = rowStride - pixelStride * width;
//
//                    int offset = 0;
//                    ByteBuffer buffer = planes[0].getBuffer();
//                    int reds = 0;
//                    for (int i = 0; i < height; ++i) {
//                        int pixel = 0;
//                        for (int j = 0; j < width; ++j) {
//                            pixel |= (buffer.get(offset) & 0xff) << 16;     // R
//                            pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
//                            pixel |= (buffer.get(offset + 2) & 0xff);       // B
////                            pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
//                            offset += pixelStride;
//                        }
//                        offset += rowPadding;
//                        reds += pixel;
//                    }
//                    Log.d(TAG, "sendImageToProcessing: " + Color.red(reds / (height*width)));
//
//                    img.close();
//                }
//
//            } catch (Exception e) {
//                e.printStackTrace();
//            } finally {
//                if (null != bitmap) {
//                    bitmap.recycle();
//                }
//                if (null != img) {
//                    img.close();
//                }
//            }


            mFileBackgroundHandler.post(mFileWriteTask);

        } catch (NullPointerException e) {
            e.printStackTrace();
        } finally {
            if (img != null)
                img.close();
            if ((System.currentTimeMillis() - start) >= 33) {
                Log.e(TAG, "Missed photo");
            }
        }
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


    public SeekBar.OnSeekBarChangeListener mOnScanAreaSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && progress > 1) {
                mScannableAreaTextView.setText(Integer.toString(progress));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (seekBar.getProgress() > 1) {
                SCANNABLE_AREA = seekBar.getProgress();
            }
        }
    };
    public SeekBar.OnSeekBarChangeListener mOnRefreshRateSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && progress > 1) {
                mGraphRefreshRateTextView.setText(Integer.toString(progress));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (seekBar.getProgress() > 1) {
                REFRESH_INTERVAL = 1000 / seekBar.getProgress();
            }
        }
    };

    private void initFileWriter() {
        mColorWriter = new ColorAverageFileWriter(getActivity(), "testing");
    }
}
