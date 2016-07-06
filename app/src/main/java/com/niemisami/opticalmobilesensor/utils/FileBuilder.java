package com.niemisami.opticalmobilesensor.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by sakrnie on 18.1.2016.
 */
public class FileBuilder {

    public static final String TAG = "FileBuilder";
    protected static String FILENAME;
    private String FILE_TIME_STRING, FOLDER_TIME_STRING;
    private static String FILE_EXTENSION = ".txt";
    protected Context mContext;
    private boolean isDataSet;
    private long mInitialTime, mInitialNanoTime;
    protected String mParentDirectory;
    protected File mDataDirectory;
    private BufferedInputStream mBufferedInputStream;


    public FileBuilder(Context context, String parentDirectoryName, long initialTime) {
        Format formatter = new SimpleDateFormat("yyyy-MM-dd HH_mm_ss");

        mInitialTime = initialTime;
        Date date = new Date(initialTime);
        FILE_TIME_STRING = date.toString();
        FOLDER_TIME_STRING = formatter.format(date);
        mContext = context;
        mParentDirectory = parentDirectoryName;
        initDirectory();
    }

    public void setInitialNanoTime(long initialNanoTime) {
        mInitialNanoTime = initialNanoTime;
    }

    public void setFileExtension(String extension) {
        if (extension != null && extension.substring(0, 1).equals('.')) {
            FILE_EXTENSION = extension;
        }
    }


    /**
     * Info file contains very basic information about the reading. Device type, patient's name, additional notes, start and end time
     */
    public void createInfoFile(long startTime, long endTime, String... basicInformation) {
//        Change FILENAME to Info_ + correct time
        setFilename("Info");
        File dataFile = new File(mDataDirectory, FILENAME);

        long recordingDuration = endTime - startTime;

        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(dataFile));

//            String data = "[HEADER]\r\n" +
//                    Build.MANUFACTURER + " " + Build.MODEL + "\r\nName: " +
//                    basicInformation[0] + "\r\nNotes: " + basicInformation[1] +
//                    "\r\n [ACC and GYRO COLUMNS]\r\n
//

            bufferedOutputStream.write(("[HEADER]\r\n").getBytes());
            bufferedOutputStream.write((Build.MANUFACTURER + " " + Build.MODEL + "\r\n").getBytes());
            bufferedOutputStream.write(("Name: " + basicInformation[0] + "\r\n").getBytes());
            bufferedOutputStream.write(("Notes: " + basicInformation[1] + "\r\n").getBytes());

            bufferedOutputStream.write(("[ACC and GYRO COLUMNS]\r\n").getBytes());
            bufferedOutputStream.write(("x, y, z, timestamp\r\n").getBytes());

            bufferedOutputStream.write(("[PPG COLUMNS]\r\n").getBytes());
            bufferedOutputStream.write(("ppg, timestamp\r\n").getBytes());

            bufferedOutputStream.write(("[TIME]\r\n").getBytes());

            bufferedOutputStream.write(("Start: " + Long.toString(startTime) + " ns\r\n").getBytes());
            bufferedOutputStream.write(("End: " + Long.toString(endTime) + " ns\r\n").getBytes());
            bufferedOutputStream.write(("Duration: " + Long.toString(recordingDuration) + " ns => " + Stopwatch.nanoToReadable(recordingDuration, 4) + "\r\n").getBytes());

            bufferedOutputStream.write(("[SENSOR'S SAMPLING RATE]\r\n").getBytes());
            bufferedOutputStream.write(("RFduino ppg: 50\r\n").getBytes());
            bufferedOutputStream.write(("Phone gyro and acc: 200\r\n").getBytes());

            bufferedOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] filePaths = new String[]{dataFile.getAbsolutePath()};
        MediaScannerConnection.scanFile(mContext.getApplicationContext(), filePaths, null, null);


    }


    /**
     * Create SUMM2 directory to the root of the device
     */
    private void initDirectory() {
        if (mDataDirectory == null) {
            File externalStorage = Environment.getExternalStorageDirectory();
            mDataDirectory = new File(externalStorage.getPath() + "/" + mParentDirectory + "/Rec_" + FOLDER_TIME_STRING + "/");
            mDataDirectory.mkdirs();
        }
    }

    private void createFile(List... numberArrays) {
//        else {
//            Toast.makeText(mContext.getApplicationContext(), "Files not created", Toast.LENGTH_SHORT);
//            return;
//        }

        File dataFile = new File(mDataDirectory, FILENAME);
        Log.d(TAG, "saved file: " + dataFile.getPath());

        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(dataFile));

//            numberArray contains x amount of arrays (short, float, int or long)
            for (int i = 0; i < numberArrays[0].size(); i++) {
                for (List array : numberArrays) {
                    if (array == numberArrays[numberArrays.length - 1]) {
                        if (array.get(i) instanceof Integer) {
                            bufferedOutputStream.write((array.get(i) + "\r\n").getBytes());
                            break;
                        }

                        bufferedOutputStream.write(((long) array.get(i) - mInitialNanoTime + "\r\n").getBytes());
                        break;
                    }
                    bufferedOutputStream.write((array.get(i) + " ").getBytes());
                }
            }

            bufferedOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        String[] filePaths = new String[]{dataFile.getAbsolutePath()};
        MediaScannerConnection.scanFile(mContext.getApplicationContext(), filePaths, null, null);

    }

    /**
     * File for storing the result of SVM classifier. Within featureMatrix the last column contains
     * the classification result and the 2nd last column contains the subject's id.
     */
    private void createFile(final float[][] featureMatrix) {
        File dataFile = new File(mDataDirectory, FILENAME);
        Log.d(TAG, "saved file: " + dataFile.getPath());

        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(dataFile));
            int result = (int) featureMatrix[0][featureMatrix[0].length-1];
            for (int i = 0; i < featureMatrix.length; i++){
                for (int j = 0; j < featureMatrix[0].length; j++){
                    bufferedOutputStream.write((featureMatrix[i][j] + " ").getBytes());
                }//for: coloums
                bufferedOutputStream.write(System.getProperty("line.separator").getBytes());
            }//for: rows

            // Write the classification result on a separate line
            bufferedOutputStream.write((result + " ").getBytes());
            bufferedOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] filePaths = new String[]{dataFile.getAbsolutePath()};
        MediaScannerConnection.scanFile(mContext.getApplicationContext(), filePaths, null, null);

    }


    /**
     * Adds time stamp after name given
     */
    public void setFilename(String name) {
        FILENAME = name + "_" + FILE_TIME_STRING + FILE_EXTENSION;
    }

    public boolean setData(List... numberArrays) throws FileNotFoundException{
//        No file to write
        if (FILENAME == null) {
            throw new FileNotFoundException();
        }
        createFile(numberArrays);
        return true;
    }

    public boolean setData(final float[][] matrix) {
//        No file to write
        if (FILENAME == null) {
            return false;
        }
        createFile(matrix);
        return true;
    }

}
