package com.niemisami.opticalmobilesensor.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
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
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.Window;
import android.widget.Toast;

import com.niemisami.opticalmobilesensor.MainActivity;
import com.niemisami.opticalmobilesensor.views.AutoFitTextureView;

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
 * Created by sakrnie on 7.7.2016.
 */
public class CameraHelper {

    private static final String TAG = CameraHelper.class.getName();

    private String mCameraId;
    private CaptureRequest mPreviewRequest;
    private Handler mCameraBackgroundHandler;
    private HandlerThread mCameraBackgroundThread;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private Context mActivityContext;
    private ImageReader mImageReader;
    private AutoFitTextureView mTextureView;
    private static final int sImageFormat = ImageFormat.YUV_420_888;
//    private static final int sImageFormat = ImageFormat.JPEG;
//    private static final int sImageFormat = ImageFormat.FLEX_RGB_888;
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener;


    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;

    private Size mPreviewSize;

    public CameraHelper(Context context, AutoFitTextureView textureView, ImageReader.OnImageAvailableListener imageAvailableListener) {
        mActivityContext = context;
        setPreviewView(textureView);
        setOnImageAvailableListener(imageAvailableListener);
    }

    private void setPreviewView(AutoFitTextureView textureView) {
        mTextureView = textureView;

    }

    private void setOnImageAvailableListener(ImageReader.OnImageAvailableListener imageAvailableListener) {
        mOnImageAvailableListener = imageAvailableListener;
    }

    /**
     * Opens the camera specified by {@link CameraHelper#mCameraId}.
     */
    public void openCamera(int width, int height) {
        startBackgroundThread();
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) mActivityContext.getSystemService(mActivityContext.CAMERA_SERVICE);
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

    public void toggleFlash(boolean turnOn) {
        try {
            if (turnOn) {
                setFlashMode(CameraMetadata.FLASH_MODE_TORCH);
            } else {
                setFlashMode(CameraMetadata.FLASH_MODE_OFF);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "toggleFlash", e);
        }
    }

    private void setFlashMode(int flashMode) throws CameraAccessException {
        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, flashMode);
        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                mCameraBackgroundHandler);
    }


    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */

    public void configureTransform(int viewWidth, int viewHeight) {

        if (mTextureView == null || mPreviewSize == null) return;

        int rotation = ((Activity) mActivityContext).getWindowManager().getDefaultDisplay().getRotation();
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
     * Closes the current {@link CameraDevice}.
     */
    public void closeCamera() {
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
            stopBackgroundThread();
        }

    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraBackgroundThread = new HandlerThread("CameraBackground");
        mCameraBackgroundThread.start();
        mCameraBackgroundHandler = new Handler(mCameraBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        try {
            mCameraBackgroundHandler.removeCallbacksAndMessages(null);
            mCameraBackgroundThread.quit();
            mCameraBackgroundThread.join();
            mCameraBackgroundThread = null;
            mCameraBackgroundHandler = null;

        } catch (InterruptedException e) {
            Log.e(TAG, "stopBackgroundThread:", e);
            mCameraBackgroundThread = null;
            mCameraBackgroundHandler = null;
        } catch (NullPointerException e) {
            Log.e(TAG, "stopBackgroundThread:", e);
            mCameraBackgroundThread = null;
            mCameraBackgroundHandler = null;
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
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
            ((Activity) mActivityContext).onBackPressed();
        }
    };

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
                            Toast.makeText(mActivityContext, "Failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) mActivityContext.getSystemService(mActivityContext.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                if (characteristics.get(LENS_FACING) == LENS_FACING_FRONT) continue;

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(sImageFormat));
                Size fittest = null;
                for(Size size : outputSizes) {
                    if(size.getWidth() == 640 || size.getHeight() == 640) {
                        fittest = size;
                        Log.d(TAG, "setUpCameraOutputs: found fit");
                        break;
                    }
                }
                if (fittest == null) {
                    Size largest = Collections.max(outputSizes, new CompareSizesByArea());
                    Log.d(TAG, "setUpCameraOutputs: found fit");
                    fittest = new Size(largest.getWidth()/16, largest.getWidth()/16);
                }


//                mHeight = largest.getWidth() / 16;
                mImageReader = ImageReader.newInstance(fittest.getWidth(), fittest.getHeight(), sImageFormat, 2);
//                mImageReader = ImageReader.newInstance(largest.getWidth() / 16, largest.getWidth() / 16, PixelFormat.RGBA_8888, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraBackgroundHandler);

                // Danger, W.R.! Attempting to use too large a preview size could exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, fittest);
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
        Window window = ((Activity) mActivityContext).getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        int statusBarHeight = rectangle.top;
        int contentViewTop =
                window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        int titleBarHeight = contentViewTop - statusBarHeight;

        Log.e(TAG, "StatusBar Height= " + statusBarHeight + " , TitleBar Height = " + titleBarHeight);
        return statusBarHeight + titleBarHeight;
    }

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
}
