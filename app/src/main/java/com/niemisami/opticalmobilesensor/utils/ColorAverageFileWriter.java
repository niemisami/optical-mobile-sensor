package com.niemisami.opticalmobilesensor.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by sakrnie on 6.7.2016.
 */
public class ColorAverageFileWriter extends FileBuilder {

    private BufferedOutputStream bufferedOutputStream;
    private File mDataFile;


    public ColorAverageFileWriter(Context context, String color) {
        super(context, "OpticalMobileSensor", System.currentTimeMillis());
        setFilename(color + "_average_per_frame");
        initDataFile();
    }

    private void initDataFile() {
        mDataFile = new File(mDataDirectory, FILENAME);
        try {
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(mDataFile));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "initDataFile", e);
        }
    }

    public void writeArray(float[] array, long timestamp) {
        try {
            StringBuilder sb = new StringBuilder();
            for (float number : array) {
                sb.append(number).append(' ');
            }
            sb.append(timestamp).append("\r\n");
            bufferedOutputStream.write((sb.toString()).getBytes());
        } catch (IOException e) {
            Log.e(TAG, "write: ", e);
        }
    }

    public void write(Number number) {
        try {
            bufferedOutputStream.write((number + "\r\n").getBytes());
        } catch (IOException e) {
            Log.e(TAG, "write: ", e);
        }
    }

    public void close() {
        try {
            Log.d(TAG, "close file");
            bufferedOutputStream.close();
            String[] filePaths = new String[]{mDataFile.getAbsolutePath()};
            MediaScannerConnection.scanFile(mContext.getApplicationContext(), filePaths, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

