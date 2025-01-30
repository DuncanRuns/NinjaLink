package xyz.duncanruns.ninjalink.util;

public class AngleUtil {
    public static double angleDifference(double from, double to) {
        double diff = (to - from) % 360;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        return diff;
    }
}
