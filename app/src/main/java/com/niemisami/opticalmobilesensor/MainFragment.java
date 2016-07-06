package com.niemisami.opticalmobilesensor;


import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;
import com.niemisami.opticalmobilesensor.utils.ColorAverageFileWriter;
import com.niemisami.opticalmobilesensor.utils.ImageProcessing;
import com.niemisami.opticalmobilesensor.views.AutoFitTextureView;
import com.niemisami.opticalmobilesensor.views.LineGraphView;

import org.achartengine.GraphicalView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;


/**
 * A simple {@link Fragment} subclass.
 */
public class MainFragment extends Fragment
        implements RadioGroup.OnCheckedChangeListener{
    private static final int sImageFormat = ImageFormat.YUV_420_888;

    private int mWantedRGBColor = Color.RED;
    private LineGraphView mLineGraph;
    private GraphicalView mGraphView;

    private RelativeLayout layout;
    public static boolean accessGranted = true;

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = MainFragment.class.getName();

    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult result) {
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                                CaptureResult partialResult) {
                    process(partialResult);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    process(result);
                }

            };
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {

                }

            };
    private String mCameraId;
    private AutoFitTextureView mTextureView;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {
//                    Log.e(TAG, "onImageAvailable: " + count++);
                    Image img = null;
                    img = reader.acquireLatestImage();
                    Result rawResult = null;
                    try {
                        if (img == null) throw new NullPointerException("cannot be null");
//                        ByteBuffer buffer = img.getPlanes()[0].getBuffer();
//                        byte[] data = new byte[buffer.remaining()];
//                        buffer.get(data);
//                        int width = img.getWidth();
//                        int height = img.getWidth();
//                        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height);
//                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
//                        TODO: Continue image processing 20.6.2016 Sakrnie

                        RectF yuvDimens = new RectF(0, 0, img.getWidth(),
                                img.getHeight());
                        final int PATCH_DIMEN = SCANNABLE_AREA; // pixels in YUV
                        // Find matching square patch of pixels in YUV and JPEG output
                        RectF tempPatch = new RectF(0, 0, PATCH_DIMEN, PATCH_DIMEN);
                        tempPatch.offset(yuvDimens.centerX() - tempPatch.centerX(),
                                yuvDimens.centerY() - tempPatch.centerY());
                        Rect yuvPatch = new Rect();
                        tempPatch.roundOut(yuvPatch);

                        mAverageColor = ImageProcessing.calculateAverageOfColor(mWantedRGBColor, yuvPatch.width(),
                                yuvPatch.height(), yuvPatch.left, yuvPatch.top, img);

                        mFileBackgroundHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mColorWriter.write(mAverageColor);
                            }
                        });

//                        ImageProcessing.convertPixelYuvToRgba(width, height, 0,0, img);

                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    } finally {
                        if (img != null)
                            img.close();
                    }
                }
            };

    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            mRepeatTask.run();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            getActivity().onBackPressed();
        }
    };

    private Size mPreviewSize;
    private HandlerThread mCameraBackgroundThread, mFileBackgroundThread;
    private Handler mCameraBackgroundHandler, mFileBackgroundHandler;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private int count;


    private ColorAverageFileWriter mColorWriter;

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mTextureView = new AutoFitTextureView(getActivity());
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(100, 100);
        mTextureView.setLayoutParams(layoutParams);
        layout = (RelativeLayout) rootView.findViewById(R.id.fragment_decoder_layout);

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
        accessGranted = true;
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThreads();
        mHandler.removeCallbacks(mRepeatTask);
        mColorWriter.close();
        super.onPause();
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getActivity().getSystemService(getActivity().CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                if (characteristics.get(LENS_FACING) == LENS_FACING_FRONT) continue;

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(sImageFormat));

                Size largest = Collections.max(outputSizes, new CompareSizesByArea());

                mImageReader = ImageReader.newInstance(largest.getWidth() / 16, largest.getWidth() / 16, sImageFormat, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraBackgroundHandler);
                // Danger, W.R.! Attempting to use too large a preview size could exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);
//                mPreviewSize = new Size(144, 144);
                Log.e(TAG, "WIDTH: " + mPreviewSize.getWidth() + " HEIGHT: " + mPreviewSize.getHeight());
                // We fit the aspect ratio of TextureView to the size of preview we picked.

//                int orientation = getResources().getConfiguration().orientation;
//                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//                } else {
                Log.d(TAG, "setUpCameraOutputs: " + MainActivity.screenParametersPoint.x + " " +
                        MainActivity.screenParametersPoint.y);
                mTextureView.setAspectRatio(MainActivity.screenParametersPoint.x,
                        MainActivity.screenParametersPoint.y - getStatusBarHeight()); //portrait only
//                }

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the camera specified by {@link MainFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) getActivity().getSystemService(getActivity().CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mCameraBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThreads() {
        mCameraBackgroundThread = new HandlerThread("CameraBackground");
        mCameraBackgroundThread.start();
        mCameraBackgroundHandler = new Handler(mCameraBackgroundThread.getLooper());

        mFileBackgroundThread = new HandlerThread("FileWriterBackground");
        mFileBackgroundThread.start();
        mFileBackgroundHandler = new Handler(mFileBackgroundThread.getLooper());
    }


    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThreads() {
        try {
            mCameraBackgroundHandler.removeCallbacksAndMessages(null);
            mCameraBackgroundThread.quitSafely();
            mCameraBackgroundThread.join();
            mCameraBackgroundThread = null;
            mCameraBackgroundHandler = null;

            mFileBackgroundHandler.removeCallbacksAndMessages(null);
            mFileBackgroundThread.quitSafely();
            mFileBackgroundThread.join();
            mFileBackgroundThread = null;
            mFileBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            mCameraBackgroundThread = null;
            mCameraBackgroundHandler = null;

            mFileBackgroundThread = null;
            mFileBackgroundHandler = null;
        } catch (NullPointerException e) {
            e.printStackTrace();
            mCameraBackgroundThread = null;
            mCameraBackgroundHandler = null;

            mFileBackgroundThread = null;
            mFileBackgroundHandler = null;
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Log.e(TAG, "mPreviewSize.getWidth(): " + mPreviewSize.getWidth() + ", mPreviewSize.getHeight(): "
                    + mPreviewSize.getHeight());

            Surface surface = new Surface(texture);
            Surface mImageSurface = mImageReader.getSurface();
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
            mPreviewRequestBuilder.addTarget(mImageSurface);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface, surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            Log.e(TAG, "onConfigured");
                            if (mCameraDevice == null) return;

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_STATE_ACTIVE_SCAN);
                                // Flash is automatically enabled when necessary.
//                                mPreviewRequestBuilder.set(CONTROL_AF_MODE,  CONTROL_AE_MODE_ON_ALWAYS_FLASH); // no need for flash now

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                                        mCameraBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getActivity(), "Failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */

    private void configureTransform(int viewWidth, int viewHeight) {

        if (mTextureView == null || mPreviewSize == null) return;

        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public int getStatusBarHeight() {
        Rect rectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        int statusBarHeight = rectangle.top;
        int contentViewTop =
                window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        int titleBarHeight = contentViewTop - statusBarHeight;

        Log.e(TAG, "StatusBar Height= " + statusBarHeight + " , TitleBar Height = " + titleBarHeight);
        return statusBarHeight + titleBarHeight;
    }

    private double mCounter = 0.0;
    private int REFRESH_INTERVAL = 100; // 1 second interval
    private double mAverageColor;
    private Handler mHandler = new Handler();

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
                try {
                    if (isChecked) {
                        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                                mCameraBackgroundHandler);
                        mFlashIcon.setImageLevel(1);
                    } else {
                        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                                mCameraBackgroundHandler);
                        mFlashIcon.setImageLevel(0);
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private void updateGraph(final double yValue) {
        mCounter++;
        mLineGraph.addValue(mCounter, yValue);
        mGraphView.repaint();
    }

    private Runnable mRepeatTask = new Runnable() {
        @Override
        public void run() {
            updateGraph(mAverageColor);
            mHandler.postDelayed(mRepeatTask, REFRESH_INTERVAL);
        }
    };

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
        mColorWriter = new ColorAverageFileWriter(getActivity(),"testing");
    }

}
