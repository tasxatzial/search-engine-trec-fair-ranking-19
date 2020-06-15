package gr.csd.uoc.hy463.themis.utils;

/**
 * Used for converting times of type long to hr, min, sec, msec
 */
public class Time {
    private long _longValue;

    public Time(long value) {
        _longValue = value;
    }

    /**
     * Returns a string representation of this Time. The string might not represent the exact
     * time due to rounding.
     * @return
     */
    public String toString() {
        double msec = toMsec(_longValue);
        if (msec > toMsec(fromHr(1))) {
            double hours = toHr(_longValue);
            double intHours = countHour(_longValue);
            double secs = toSec(fromHr(hours) - fromHr(intHours));
            double mins = toMin(fromSec(secs));
            return intHours + "h " + roundToDecimal(mins, 0) + "m";
        }
        if (msec > toMsec(fromMin(1))) {
            double mins = toMin(_longValue);
            double intMins = countMin(_longValue);
            double secs = toSec(fromMin(mins) - fromMin(intMins));
            return intMins + "m " + roundToDecimal(secs, 0) + "s";
        }
        if (msec > toMsec(fromSec(1))) {
            double secs = toSec(_longValue);
            double intSecs = countSec(_longValue);
            double msecs = toMsec(fromSec(secs) - fromSec(intSecs));
            return intSecs + "s " + roundToDecimal(msecs, 0) + "ms";
        }
        if (msec > 10) {
            return roundToDecimal(msec, 0) + "ms";
        }
        if (msec > 1) {
            return roundToDecimal(msec, 1) + "ms";
        }
        return roundToDecimal(msec, 2) + "ms";
    }

    /* Rounds the specified value to numDigits decimal digits */
    public static double roundToDecimal(double value, int numDigits) {
        double pow = Math.pow(10, numDigits);
        return Math.round(value * pow) / pow;
    }

    /**
     * Returns how many hr the specified long value has
     * @param value
     * @return
     */
    public static double countHour(long value) {
        return Math.floor(toHr(value));
    }

    /**
     * Returns how many min the specified long value has
     * @param value
     * @return
     */
    public static double countMin(long value) {
        return Math.floor(toMin(value));
    }

    /**
     * Returns how many sec the specified long value has
     * @param value
     * @return
     */
    public static double countSec(long value) {
        return Math.floor(toSec(value));
    }

    /**
     * Returns how many msec the specified long value has
     * @param value
     * @return
     */
    public static double countMsec(long value) {
        return Math.floor(toMsec(value));
    }

    /**
     * Returns the long number from a given time in hr
     * @param value
     * @return
     */
    public static long fromHr(double value) {
        return (long) (value * 3.6e12);
    }

    /**
     * Returns the long number from a given time in min
     * @param value
     * @return
     */
    public static long fromMin(double value) {
        return (long) (value * 6e10);
    }

    /**
     * Returns the long number from a given time in sec
     * @param value
     * @return
     */
    public static long fromSec(double value) {
        return (long) (value * 1e9);
    }

    /**
     * Returns the long number from a given time in msec
     * @param value
     * @return
     */
    public static long fromMsec(double value) {
        return (long) (value * 1e6);
    }

    /**
     * Returns the specified time in msec
     * @return
     */
    public static double toMsec(long value) {
        return value / 1e6;
    }

    /**
     * Returns the specified time in sec
     * @return
     */
    public static double toSec(long value) {
        return value / 1e9;
    }

    /**
     * Returns the specified time in min
     * @param value
     * @return
     */
    public static double toMin(long value) {
        return value / 6e10;
    }

    /**
     * Returns the specified time in hr
     * @return
     */
    public static double toHr(long value) {
        return value / 3.6e12;
    }

    /**
     * Adds the specified time to this Time. The new long value of this Time is increased by
     * the long value of the specified time
     * @param time
     */
    public void addTime(Time time) {
        _longValue += time.getValue();
    }

    /**
     * Returns the long value associated with this Time
     * @return
     */
    public long getValue() {
        return _longValue;
    }
}
