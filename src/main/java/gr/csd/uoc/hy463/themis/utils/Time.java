package gr.csd.uoc.hy463.themis.utils;

/**
 * Used for converting times of type long to msec and sec.
 */
public class Time {
    private long _longValue;

    public Time(long value) {
        _longValue = value;
    }

    /* Rounds the specified value to numDigits decimal digits */
    private double roundToDecimal(double value, int numDigits) {
        double pow = Math.pow(10, numDigits);
        return Math.round(value * pow) / pow;
    }

    /**
     * Returns a string representation of this Time.
     * @return
     */
    public String toString() {
        double msec = toMsec();
        if (msec > 100000) {
            return roundToDecimal(toSec(), 0) + " s";
        }
        if (msec > 10000) {
            return roundToDecimal(toSec(), 1) + " s";
        }
        if (msec > 1000) {
            return roundToDecimal(toSec(), 2) + " s";
        }
        if (msec > 100) {
            return roundToDecimal(msec, 0) + " ms";
        }
        if (msec > 10) {
            return roundToDecimal(msec, 1) + " ms";
        }
        return roundToDecimal(msec, 2) + " ms";
    }

    /**
     * Returns this Time in msec
     * @return
     */
    public double toMsec() {
        return _longValue / 1e6;
    }

    /**
     * Returns this Time in sec
     * @return
     */
    public double toSec() {
        return _longValue / 1e9;
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
