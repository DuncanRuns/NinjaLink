package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class F3CData {
    private static final Gson GSON = new Gson();

    public final Dimension dimension;
    public final long x;
    public final long y;
    public final long z;
    public final double yaw;
    public final double pitch;

    public F3CData(Dimension dimension, long x, long y, long z, double yaw, double pitch) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static F3CData fromJson(String string) throws JsonSyntaxException {
        return GSON.fromJson(string, F3CData.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
