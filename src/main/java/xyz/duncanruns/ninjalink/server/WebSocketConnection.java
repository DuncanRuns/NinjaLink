package xyz.duncanruns.ninjalink.server;

import org.java_websocket.WebSocket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WebSocketConnection implements Connection {
    private final WebSocket webSocket;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    public WebSocketConnection(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    public void sendString(String string) {
        webSocket.send(string);
    }

    @Override
    public String receiveString() throws InterruptedException {
        return messageQueue.take();
    }

    @Override
    public void close() {
        webSocket.close();
    }

    public void queue(String s) {
        messageQueue.add(s);
    }
}
