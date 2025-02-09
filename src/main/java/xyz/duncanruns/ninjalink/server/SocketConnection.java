package xyz.duncanruns.ninjalink.server;

import xyz.duncanruns.ninjalink.util.SocketUtil;

import java.io.IOException;
import java.net.Socket;

public class SocketConnection implements Connection {
    private final Socket socket;

    public SocketConnection(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void sendString(String string) throws IOException {
        SocketUtil.sendStringWithLength(socket, string);
    }

    @Override
    public String receiveString() throws IOException {
        return SocketUtil.receiveStringWithLength(socket);
    }

    @Override
    public String receiveString(int maxLength) throws IOException {
        return SocketUtil.receiveStringWithLength(socket, maxLength);
    }

    @Override
    public void close() {
        SocketUtil.carelesslyClose(socket);
    }
}
