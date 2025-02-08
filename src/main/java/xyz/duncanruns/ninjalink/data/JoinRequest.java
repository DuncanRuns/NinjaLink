package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;

public class JoinRequest {
    private static final Gson GSON = new Gson();

    @Nullable
    public final String nickname;
    public final String roomName;
    public final String roomPass;
    public final int protocolVersion;

    public JoinRequest(@Nullable String nickname, String roomName, String roomPass, int protocolVersion) {
        this.nickname = nickname == null ? null : nickname.trim();
        this.roomName = roomName;
        this.roomPass = roomPass;
        this.protocolVersion = protocolVersion;
    }

    public static JoinRequest fromJson(String string) throws JsonSyntaxException {
        return GSON.fromJson(string, JoinRequest.class);
    }

    public boolean isWatcher() {
        return nickname == null;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
