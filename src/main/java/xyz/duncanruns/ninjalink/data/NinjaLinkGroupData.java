package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;

public class NinjaLinkGroupData {
    private static final Gson GSON = new Gson();

    public Map<String, PlayerData> playerDataMap = new HashMap<>();

    public static NinjaLinkGroupData fromJson(String string) throws JsonSyntaxException {
        return GSON.fromJson(string, NinjaLinkGroupData.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

}
