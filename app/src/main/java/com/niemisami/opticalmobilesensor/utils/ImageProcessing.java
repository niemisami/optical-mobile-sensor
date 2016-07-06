package com.niemisami.opticalmobilesensor.utils;

import android.graphics.Color;
import android.media.Image;

import com.niemisami.opticalmobilesensor.SignalProcessing.DSP;

import java.nio.ByteBuffer;

/**
 * Created by sakrnie on 20.6.2016.
 * Contains different algorithms for extracting YUV-planes to RGB
 */
public class ImageProcessing {

    private static final String TAG = ImageProcessing.class.getSimpleName();


    private static int decodeYUV420SPtoRedSum(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return 0;

        final int frameSize = width * height;

        int sum = 0;
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & yuv420sp[yp]) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;

                int pixel = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
                int red = (pixel >> 16) & 0xff;
                sum += red;
//                int green = (pixel >> 8) & 0xff;
//                sum += green;
//                int blue = pixel  & 0xff;
//                sum += blue;
            }
        }

        return sum;
    }

    /**
     * Given a byte array representing a yuv420sp image, determine the average
     * amount of red in the image. Note: returns 0 if the byte array is NULL.
     *
     * @param yuv420sp Byte array representing a yuv420sp image
     * @param width    Width of the image.
     * @param height   Height of the image.
     * @return int representing the average amount of red in the image.
     */
    public static int decodeYUV420SPtoRedAvg(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return 0;

        final int frameSize = width * height;

        int sum = decodeYUV420SPtoRedSum(yuv420sp, width, height);
        return (sum / frameSize);
    }

    /**
     * Convert a rectangular patch in a YUV image to an ARGB color array.
     *
     * @param w        width of the patch.
     * @param h        height of the patch.
     * @param wOffset  offset of the left side of the patch.
     * @param hOffset  offset of the top of the patch.
     * @param yuvImage a YUV image to select a patch from.
     * @return the image patch converted to RGB as an ARGB color array.
     */
    public static int[] convertPixelYuvToRgba(int w, int h, int wOffset, int hOffset,
                                              Image yuvImage) {
        final int CHANNELS = 3; // yuv
        final float COLOR_RANGE = 255f;

        Image.Plane[] planes = yuvImage.getPlanes();
        Image.Plane yPlane = planes[0];
        Image.Plane cbPlane = planes[1];
        Image.Plane crPlane = planes[2];

        ByteBuffer yBuf = yPlane.getBuffer();
        int yPixStride = yPlane.getPixelStride();
        int yRowStride = yPlane.getRowStride();

        ByteBuffer cbBuf = cbPlane.getBuffer();
        int cbPixStride = cbPlane.getPixelStride();
        int cbRowStride = cbPlane.getRowStride();

        ByteBuffer crBuf = crPlane.getBuffer();
        int crPixStride = crPlane.getPixelStride();
        int crRowStride = crPlane.getRowStride();

        int[] output = new int[w * h];
        byte[] yRow = new byte[yPixStride * w];
        byte[] cbRow = new byte[cbPixStride * w / 2];
        byte[] crRow = new byte[crPixStride * w / 2];
        yBuf.mark();
        cbBuf.mark();
        crBuf.mark();
        int initialYPos = yBuf.position();
        int initialCbPos = cbBuf.position();
        int initialCrPos = crBuf.position();
        int outputPos = 0;
        for (int i = hOffset; i < hOffset + h; i++) {
            yBuf.position(initialYPos + i * yRowStride + wOffset * yPixStride);
            yBuf.get(yRow);
            if ((i & 1) == (hOffset & 1)) {

                cbBuf.position(initialCbPos + (i / 2) * cbRowStride + wOffset * cbPixStride / 2);
                cbBuf.get(cbRow);
                crBuf.position(initialCrPos + (i / 2) * crRowStride + wOffset * crPixStride / 2);
                crBuf.get(crRow);

            }
            for (int j = 0, yPix = 0, crPix = 0, cbPix = 0; j < w; j++, yPix += yPixStride) {
                float y = yRow[yPix] & 0xFF;
                float cb = cbRow[cbPix] & 0xFF;
                float cr = crRow[crPix] & 0xFF;
                // convert YUV -> RGB (from JFIF's "Conversion to and from RGB" section)
                int r = (int) Math.max(0.0f, Math.min(COLOR_RANGE, y + 1.402f * (cr - 128)));
                int g = (int) Math.max(0.0f,
                        Math.min(COLOR_RANGE, y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128)));
                int b = (int) Math.max(0.0f, Math.min(COLOR_RANGE, y + 1.772f * (cb - 128)));
                // Convert to ARGB pixel color (use opaque alpha)
                output[outputPos++] = Color.rgb(r, g, b);
                if ((j & 1) == 1) {
                    crPix += crPixStride;
                    cbPix += cbPixStride;
                }
            }
        }
        yBuf.rewind();
        cbBuf.rewind();
        crBuf.rewind();
        return output;
    }

    public static double calculateAverageOfColor(int color, int w, int h, int wOffset, int hOffset,
                                              Image yuvImage) {
        int[] colorArray = convertPixelYuvToRgba(w, h, wOffset, hOffset, yuvImage);
        int[] averages = new int[colorArray.length];
        int index = 0;
        for (int i : colorArray) {
            switch (color) {
                case 0: //Calculate average of selected color
                    averages[index] = i;
                    break;
                case Color.RED:
                    averages[index] = Color.red(i);
                    break;
                case Color.GREEN:
                    averages[index] = Color.green(i);
                    break;
                case Color.BLUE:
                    averages[index] = Color.blue(i);
                    break;
            }
            index++;
        }
        return DSP.getAverage(averages);
    }
}
