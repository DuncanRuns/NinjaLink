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
    @Nullable
    public final F3CData f3CData;

    public ClientData(Type type) {
        this(type, null, null);
    }

    public ClientData(F3CData f3CData) {
        this(Type.F3C, null, f3CData);
    }

    public ClientData(NinjabrainBotEventData ninjabrainBotEventData) {
        this(Type.NINJABRAIN_BOT_EVENT_DATA, ninjabrainBotEventData, null);
    }

    public ClientData(Type type, @Nullable NinjabrainBotEventData ninjabrainBotEventData, @Nullable F3CData f3CData) {
        this.type = type;
        this.ninjabrainBotEventData = ninjabrainBotEventData;
        this.f3CData = f3CData;
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
        DISCONNECT,
        F3C,
        @Deprecated PING, // Now handled by websocket
    }
}
