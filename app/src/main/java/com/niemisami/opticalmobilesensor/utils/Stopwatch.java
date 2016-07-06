package com.niemisami.opticalmobilesensor.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Stopwatch {

    private static final String TAG = "Stopwatch";
    private long startTime;
    private long elapsedTime;
    private Handler mHandler;
    private final long REFRESH_RATE = 100l;
    private String minutes, seconds, milliseconds;
    private long secs, mins, msecs;
    private boolean mIsRunning, mAutoStopEnabled, mVibratePhone;
    private long mAutoStopTime;
    private TextView tw;
    private Looper mLooper;
    private Activity mActivity;
    private StopwatchListener mStopwatchListener;
    private int mColorEnabled, mColorDisabled;

    /**
     * Consturcts new stopwatch.
     *
     * @param tw TextView element to bind the stopwatch
     */
    public Stopwatch(Activity activity, TextView tw) {
        mActivity = activity;
        initBackgroundThread();
        this.tw = tw;
    }

    private void initBackgroundThread() {
        HandlerThread stopwatchThread = new HandlerThread("StopwatchThread");
        stopwatchThread.start();
        mLooper = stopwatchThread.getLooper();
        mHandler = new Handler(mLooper);
    }

    public void setStopwatchColors(int resourceColorEnabled, int resourceColorDisabled) {
        mColorEnabled = resourceColorEnabled;
        mColorDisabled = resourceColorDisabled;
        initStopwatchFace();
    }

    private void initStopwatchFace() {
        Spannable spannableTime = new SpannableString("-00:00");
        colorizeStopwatch(spannableTime, mColorDisabled);
        tw.setText(spannableTime);
    }


    /**
     * Starts the clock
     */
    public void start() {
        mIsRunning = true;
        mStopwatchListener.onStartStopwatch();
        startTime = System.currentTimeMillis() + 4999;

//        startTime = System.currentTimeMillis();
        mHandler.removeCallbacks(startTimer);
        mHandler.postDelayed(startTimer, 0);
    }

    /**
     * Stops the clock
     */
    public void stop() {
        mIsRunning = false;
        //Run on ui because stop() might get called from background thread
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStopwatchListener.onStopStopwatch();

            }
        });
        mHandler.removeCallbacks(startTimer);
        if (mVibratePhone) {
            vibratePhone(300);
        }

    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public void destroy() {
        stop();
        mLooper.quit();
    }

    public void setAutoStop(int autoStopInMin) {
        mAutoStopTime = 60 * autoStopInMin;
//        mAutoStopTime = 6 * autoStopInMin; // for testing
        mAutoStopEnabled = true;
    }

    public void disableAutoStop() {
        mAutoStopEnabled = false;
    }

    public boolean isAutoStopEnabled() {
        return mAutoStopEnabled;
    }

    /**
     * Resets the clock
     */
    public void reset() {
        mIsRunning = false;
    }

    /**
     * Returns the time in string
     *
     * @return elapsed time
     */
    public String getTime() {

        if (mins < 0 || secs < 0) {
            return "-" + minutes + ":" + seconds;
        } else {
            return "-" + minutes + ":" + seconds;
        }
    }

    /**
     * Returns the time in seconds (long)
     *
     * @return elapsed time
     */
    public long getTimeInSeconds() {
        return (secs + (60 * mins));
    }

    /**
     * Updates the values
     *
     * @param time
     */
    private void updateTimer(long time) {
        secs = (time / 1000);
        mins = ((time / 1000) / 60);

        secs = secs % 60;
        seconds = String.valueOf(Math.abs(secs));
        if (secs == 0) {
            seconds = "00";
        }
        if (secs < 10 && secs > 0) {
            seconds = "0" + seconds;
        }

        if (secs > -10 && secs < 0)
            seconds = "0" + seconds;

        mins = mins % 60;
        minutes = String.valueOf(Math.abs(mins));
        if (mins == 0) {
            minutes = "00";
        }
        if (mins < 10 && mins > 0) {
            minutes = "0" + minutes;
        }

        final Spannable spannableTime = new SpannableString(this.getTime());
        if (mins >= 0 && secs >= 0) {
            colorizeStopwatch(spannableTime, mColorDisabled);
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tw.setText(spannableTime);
            }
        });
    }


    private void colorizeStopwatch(Spannable spannableText, int color) {
        spannableText.setSpan(new ForegroundColorSpan(color), 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    /**
     * Runs the time and invokes StopwatchListener.onTimeZero when time is more than zero
     */
    private Runnable startTimer = new Runnable() {
        @Override
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime;
//            This ensures that timer doesn't calculate two zeros
            if (elapsedTime < 0) {
                updateTimer(elapsedTime - 1000);
                mHandler.postDelayed(this, REFRESH_RATE);

            } else {
                if (elapsedTime <= 100) {
                    mStopwatchListener.onTimeZero();
                }
                updateTimer(elapsedTime);
                mHandler.postDelayed(this, REFRESH_RATE);
            }

            if (mAutoStopEnabled && getTimeInSeconds() >= mAutoStopTime) {
                stop();
            }
        }
    };

    public void setStopwatchListener(StopwatchListener stopwatchListener) {
        this.mStopwatchListener = stopwatchListener;
    }

    public interface StopwatchListener {
        void onStartStopwatch();

        void onStopStopwatch();

        void onTimeZero();
    }


    /**
     * Help convert nano time to human readable form
     *
     * @param accuracy value of how many values wanted to see, max 4: min, sec, millis, nanos
     */
    public static String nanoToReadable(Long nanoTime, int accuracy) {
        String readableTime = "";
        if (accuracy > 0)
            readableTime += Long.toString(TimeUnit.NANOSECONDS.toMinutes(nanoTime)) + "min ";
        if (accuracy > 1)
            readableTime += Long.toString(TimeUnit.NANOSECONDS.toSeconds(nanoTime) % 60) + "sec ";
        if (accuracy > 2)
            readableTime += Long.toString(TimeUnit.NANOSECONDS.toMillis(nanoTime) % 1000) + "millisec ";
        if (accuracy > 3)
            readableTime += Long.toString(nanoTime % 1000000) + "ns ";

        return readableTime;

    }

    public static String millisToReadable(Long nanoTime, int accuracy) {
        String readableTime = "";
        if (accuracy > 0)
            readableTime += Long.toString(TimeUnit.MILLISECONDS.toMinutes(nanoTime)) + "min ";
        if (accuracy > 1)
            readableTime += Long.toString(TimeUnit.MILLISECONDS.toSeconds(nanoTime) % 60) + "sec ";
        if (accuracy > 2)
            readableTime += Long.toString(TimeUnit.MILLISECONDS.toMillis(nanoTime) % 1000) + "millisec ";
        if (accuracy > 3)
            readableTime += Long.toString(nanoTime % 1000000) + "ns ";

        return readableTime;

    }

    public static final String DAY_MONTH_YEAR = "dd.MM.yyyy";
    public static final String HOUR_MIN = "HH:mm";

    /**
     * Formats millisecond time to format given dd.MM.yyyy e.g. 01.01.2016
     */
    public static String formatDatePretty(long millisTime, String FORMAT) {
        Format formatter = new SimpleDateFormat(FORMAT);
        Date date = new Date(millisTime);
        return formatter.format(date);
    }

    public void enableVibration(boolean vibrate) {
        mVibratePhone = vibrate;
    }

    private void vibratePhone(int milliseconds) {
        //        Small vibration after recording ends
        Vibrator v = (Vibrator) mActivity.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(milliseconds);
    }
}
