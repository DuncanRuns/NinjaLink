package xyz.duncanruns.ninjalink.server;

import java.io.IOException;

public interface Connection {
    void sendString(String string) throws IOException;

    String receiveString() throws IOException, InterruptedException;

    void close();
}
