package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class JoinRequestResponse {
    private static final Gson GSON = new Gson();

    public final boolean accepted;
    public final String message;

    public JoinRequestResponse(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static JoinRequestResponse fromJson(String string) throws JsonSyntaxException {
        return GSON.fromJson(string, JoinRequestResponse.class);
    }
}
