package com.dobi.walkingsynth.accelerometer;

import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.Date;

/**
 * Computing and processing accelerometer data.
 */
public class AccelerometerProcessing implements OnThresholdChangeListener {

    private static final String TAG = AccelerometerProcessing.class.getSimpleName();

    private static AccelerometerProcessing instance = null;

    public static AccelerometerProcessing getInstance() {

        if (instance == null)
            return new AccelerometerProcessing();
        return instance;
    }

    /**
     * Step detecting parameter. How many periods it is sleeping.
     * If DELAY_GAME: T ~= 20ms => f = 50Hz
     * and MAX_TEMPO = 240bpms
     * then:
     * 60bpm - 1000milliseconds
     * 240bpm - 250milliseconds
     *
     * n - periods
     * n = 250msec / T
     * n = 250 / 20 ~= 12
     */
    private static final int INACTIVE_PERIODS = 12;
    public static final float THRESH_INIT_VALUE = 12.72f;

    // dynamic variables
    private int mInactiveCounter = 0;
    public boolean isActiveCounter = true;

    private static double mThresholdValue = THRESH_INIT_VALUE;
    private double[] mAccelValues = new double[AccelerometerSignals.count];
    private double[] mAccelLastValues = new double[AccelerometerSignals.count];

    private SensorEvent mEvent;

    // computational variables
    private double[] gravity = new double[3];
    private double[] linear_acceleration = new double[3];
    private ScalarKalmanFilter filtersCascade[] = new ScalarKalmanFilter[3];

    /**
     * Gets the current SensorEvent data.
     * @param e the mEvent.
     */
    public void setEvent(SensorEvent e) {
        mEvent = e;
    }

    public double getThresholdValue() {
        Log.d(TAG,"Getting Threshold: " + mThresholdValue);
        return mThresholdValue;
    }


    /**
     * Get event time.
     * @see <a href="http://stackoverflow.com/questions/5500765/accelerometer-sensorevent-timestamp">To miliseconds.</a>
     * @return time in milliseconds
     */
    public long timestampToMilliseconds() {
        return (new Date()).getTime() + (mEvent.timestamp - System.nanoTime()) / 1000000L;
    }


    /**
     * Initializes the Scalar Kalman Filters one after another.
     */
    public void initKalman() {
        // set filter
        filtersCascade[0] = new ScalarKalmanFilter(1, 1, 0.01f, 0.0025f);
        filtersCascade[1] = new ScalarKalmanFilter(1, 1, 0.01f, 0.0025f);
        filtersCascade[2] = new ScalarKalmanFilter(1, 1, 0.01f, 0.0025f);
    }

    /**
     * Smoothes the signal from accelerometer.
     */
    private double filter(double measurement){
        double f1 = filtersCascade[0].correct(measurement);
        double f2 = filtersCascade[1].correct(f1);
        double f3 = filtersCascade[2].correct(f2);
        return f3;
    }

    public double calcKalman(int i) {
        mAccelValues[i] = filter(mAccelValues[i]);
        //mAccelValues[i] = Math.abs(mAccelValues[i] - mLastOne);
        //mLastOne = mAccelValues[i];
        return mAccelValues[i];
    }

    /**
     * Filters the signal out of gravity impact.
     */
    public void calcFilterGravity() {
        // In this example, alpha is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the mEvent delivery rate.
        final float alpha = 0.9f;

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * mEvent.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * mEvent.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * mEvent.values[2];
    }

    /**
     * Vector Magnitude |V| = sqrt(x^2 + y^2 + z^2)
     * @param i signal identifier.
     * @return the output vector.
     */
    public double calcMagnitudeVector(int i) {
        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = mEvent.values[0] - gravity[0];
        linear_acceleration[1] = mEvent.values[1] - gravity[1];
        linear_acceleration[2] = mEvent.values[2] - gravity[2];

        mAccelValues[i] = Math.sqrt(
                linear_acceleration[0] * linear_acceleration[0] +
                linear_acceleration[1] * linear_acceleration[1] +
                linear_acceleration[2] * linear_acceleration[2]);
        return mAccelValues[i];
    }

    /**
     * Difference from gravity: (x^2 + y^2 + z^2) / G^2
     * @param i signal identifier.
     * @return the output vector.
     */
    public double calcGravityDiff(int i) {
        mAccelValues[i] = (
                mEvent.values[0] * mEvent.values[0] +
                mEvent.values[1] * mEvent.values[1] +
                mEvent.values[2] * mEvent.values[2]) /
                (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        return mAccelValues[i];
    }

    /**
     * Exponential Moving average.
     * @see <a href="http://stackoverflow.com/questions/16392142/android-accelerometer-profiling">Stack Overflow discussion</a>
     * @param i signal identifier.
     * @return the output vector.
     */
    public double calcExpMovAvg(int i) {
        final double alpha = 0.1;
        mAccelValues[i] = alpha * mAccelValues[i] + (1 - alpha) * mAccelLastValues[i];
        mAccelLastValues[i] = mAccelValues[i];
        return mAccelValues[i];
    }

    /**
     * My step detection algorithm.
     * When the value is over the threshold, the step is found and the algorithm sleeps for
     * the specified distance which is {@link #INACTIVE_PERIODS this }.
     * @param i signal identifier.
     * @return step found / not found
     */
    public boolean stepDetected(int i) {
        if (mInactiveCounter == INACTIVE_PERIODS) {
            mInactiveCounter = 0;
            if (!isActiveCounter)
                isActiveCounter = true;
        }
        if (mAccelValues[i] > mThresholdValue) {
            if (isActiveCounter) {
                mInactiveCounter = 0;
                isActiveCounter = false;
                return true;
            }
        }
        ++mInactiveCounter;
        return false;
    }

    @Override
    public void onThresholdChange(double value) {
        Log.d(TAG, "Current Threshold is: " + value);
        mThresholdValue = value;
    }
}
