package com.niemisami.opticalmobilesensor.utils;

import android.graphics.ImageFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Size;
import android.view.Surface;

import com.silentcircle.silentphone.ScriptC_yuv2rgb;

/**
 * Created by sakrnie on 14.7.2016.
 */
public class RgbConversion implements Allocation.OnBufferAvailableListener {
    private Allocation mInputAllocation;
    private Allocation mOutputAllocation;
    private Allocation mOutputAllocationInt;
    private Allocation mScriptAllocation;
    private Size mSizeVideoCall;
    private ScriptC_yuv2rgb mScriptC;

    private int[] mOutBufferInt;
    private long mLastProcessed;

    private CameraHelper.OnFrameProcessedListener mFrameCallback;

    private final int mFrameEveryMs;

    public RgbConversion(RenderScript rs, Size dimensions,
                         CameraHelper.OnFrameProcessedListener frameCallback, int frameMs) {
        mSizeVideoCall = dimensions;
        mFrameCallback = frameCallback;
        mFrameEveryMs = frameMs;

        createAllocations(rs);

        mInputAllocation.setOnBufferAvailableListener(this);

        mScriptC = new ScriptC_yuv2rgb(rs);
        mScriptC.set_gCurrentFrame(mInputAllocation);
        mScriptC.set_gIntFrame(mOutputAllocationInt);
    }

    private void createAllocations(RenderScript rs) {

        mOutBufferInt =
                new int[mSizeVideoCall.getWidth() * mSizeVideoCall.getHeight()];

        final int width = mSizeVideoCall.getWidth();
        final int height = mSizeVideoCall.getHeight();

        Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.YUV(rs));
        yuvTypeBuilder.setX(width);
        yuvTypeBuilder.setY(height);
        yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
        mInputAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(),
                Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

        Type rgbType = Type.createXY(rs, Element.RGBA_8888(rs), width, height);
        Type intType = Type.createXY(rs, Element.U32(rs), width, height);

        mScriptAllocation = Allocation.createTyped(rs, rgbType,
                Allocation.USAGE_SCRIPT);
        mOutputAllocation = Allocation.createTyped(rs, rgbType,
                Allocation.USAGE_IO_OUTPUT | Allocation.USAGE_SCRIPT);
        mOutputAllocationInt = Allocation.createTyped(rs, intType,
                Allocation.USAGE_SCRIPT);
    }

    public Surface getInputSurface() {
        return mInputAllocation.getSurface();
    }

    public void setOutputSurface(Surface output) {
        mOutputAllocation.setSurface(output);
    }

    @Override
    public void onBufferAvailable(Allocation a) {
        // Get the new frame into the input allocation
        mInputAllocation.ioReceive();

        // Run processing pass if we should send a frame
        final long current = System.currentTimeMillis();
        if ((current - mLastProcessed) >= mFrameEveryMs) {
            mScriptC.forEach_yuv2rgbFrames(mScriptAllocation, mOutputAllocation);
            if (mFrameCallback != null) {
                mOutputAllocationInt.copyTo(mOutBufferInt);
                mFrameCallback.onFrameArrayInt(mOutBufferInt, current);
            }
            mLastProcessed = current;
        }
    }
}