package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;

import static xyz.duncanruns.ninjalink.Constants.NAME_PATTERN;
import static xyz.duncanruns.ninjalink.Constants.PASSWORD_PATTERN;

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

    /**
     * Checks if the strings match their patterns and have valid combinations of emptiness
     */
    public boolean isValid() {
        if (nickname != null && !NAME_PATTERN.matcher(nickname).matches()) return false;
        if (roomName.isEmpty()) return roomPass.isEmpty();
        if (!NAME_PATTERN.matcher(roomName).matches()) return false;
        return PASSWORD_PATTERN.matcher(roomPass).matches();
    }

    /**
     * Helper method to check if join request data will be valid
     */
    public static boolean isValidData(String nickname, String roomName, String roomPass) {
        return new JoinRequest(nickname, roomName, roomPass, -1).isValid();
    }
}
