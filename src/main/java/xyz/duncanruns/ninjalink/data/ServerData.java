package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;

/**
 * Data from the client to be sent to the server.
 */
public class ServerData {
    private static final Gson GSON = new Gson();

    public final Type type;
    @Nullable
    public final NinjaLinkGroupData ninjaLinkGroupData;
    @Nullable
    public final String message;

    public ServerData(NinjaLinkGroupData groupData) {
        this(Type.GROUP_DATA, groupData, null);
    }

    public ServerData(Type type, String message) {
        this(type, null, message);
    }

    public ServerData(Type type, @Nullable NinjaLinkGroupData ninjaLinkGroupData, @Nullable String message) {
        this.type = type;
        this.ninjaLinkGroupData = ninjaLinkGroupData;
        this.message = message;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ServerData fromJson(String string) throws JsonSyntaxException {
        return GSON.fromJson(string, ServerData.class);
    }

    public enum Type {
        GROUP_DATA,
        DISCONNECT,
    }
}
