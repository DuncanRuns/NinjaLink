package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;

/**
 * Data from the client to be sent to the server.
 */
public class ClientData {
    private static final Gson GSON = new Gson();

    public final Type type;
    @Nullable
    public final NinjabrainBotEventData ninjabrainBotEventData;

    public ClientData(Type type) {
        this(type, null);
    }

    public ClientData(NinjabrainBotEventData ninjabrainBotEventData) {
        this(Type.NINJABRAIN_BOT_EVENT_DATA, ninjabrainBotEventData);
    }

    public ClientData(Type type, @Nullable NinjabrainBotEventData ninjabrainBotEventData) {
        this.type = type;
        this.ninjabrainBotEventData = ninjabrainBotEventData;
    }

    public static ClientData fromJson(String string) throws JsonSyntaxException {
        return GSON.fromJson(string, ClientData.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public enum Type {
        NINJABRAIN_BOT_EVENT_DATA,
        CLEAR,
        PING,
        DISCONNECT,
    }
}
