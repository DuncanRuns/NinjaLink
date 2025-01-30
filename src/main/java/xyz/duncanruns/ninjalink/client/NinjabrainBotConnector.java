package xyz.duncanruns.ninjalink.client;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NinjabrainBotConnector {
    private static final String STRONGHOLD_EVENTS_LOCAL_ENDPOINT = "http://localhost:52533/api/v1/stronghold/events";

    private ConnectionState connectionState = ConnectionState.CLOSED;
    private final Consumer<String> onEvent;
    private final BiConsumer<ConnectionState, ConnectionState> onConnectionStateChange;
    private boolean running = false;
    private EventSource eventSource = null;


    public NinjabrainBotConnector(Consumer<String> onEvent, BiConsumer<ConnectionState, ConnectionState> onConnectionStateChange) {
        this.onConnectionStateChange = onConnectionStateChange;
        this.onEvent = onEvent;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        new Thread(this::reconnectLoop).start();
    }

    private void reconnectLoop() {
        while (running) {
            synchronized (this) {
                if (this.connectionState == ConnectionState.CLOSED) {
                    startNewConnection();
                }
            }
            sleep(3000);
        }
    }

    public synchronized void close() {
        this.running = false;
        eventSource.cancel();
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void startNewConnection() {
        setConnectionState(ConnectionState.CONNECTING);
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build();
        HttpUrl url = HttpUrl.get(STRONGHOLD_EVENTS_LOCAL_ENDPOINT);
        Request request = new Request.Builder().url(url).build();
        this.eventSource = EventSources.createFactory(client).newEventSource(request, new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                setConnectionState(ConnectionState.CONNECTED);
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
                onEvent.accept(data);
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                eventSource.cancel();
                setConnectionState(ConnectionState.CLOSED);
                client.dispatcher().executorService().shutdown();
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                onClosed(eventSource);
            }
        });
    }

    private void setConnectionState(ConnectionState connectionState) {
        ConnectionState previousState = this.connectionState;
        if (previousState == connectionState) return;
        this.connectionState = connectionState;
        onConnectionStateChange.accept(previousState, connectionState);
    }

    public enum ConnectionState {
        CLOSED,
        CONNECTING,
        CONNECTED
    }

    // Method to test the NinjabrainBotConnector, simply outputs connection info and stronghold event data for 2 minutes
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Showing NinjabrainBotConnector output for 2 minutes...");
        NinjabrainBotConnector ninjabrainBotConnector = new NinjabrainBotConnector(System.out::println, (previous, state) -> System.out.println(state));
        ninjabrainBotConnector.start();
        Thread.sleep(120_000);
        ninjabrainBotConnector.close();
    }
}
