package xyz.duncanruns.ninjalink.client.clipboard;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ClipboardListener {
    private final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private ScheduledExecutorService executor = null;
    private final Runnable onStart;

    public ClipboardListener(int delay, Consumer<String> onChange) {
        this.onStart = () -> {
            AtomicReference<String> last = new AtomicReference<>(getClipboardContents().orElse(""));
            executor.scheduleWithFixedDelay(() -> getClipboardContents().ifPresent(s -> {
                if (!s.equals(last.get())) {
                    last.set(s);
                    onChange.accept(s);
                }
            }), delay, delay, TimeUnit.MILLISECONDS);
        };
    }

    @NotNull
    private static ScheduledExecutorService getExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Clipboard Listener");
            thread.setDaemon(true);
            return thread;
        });
    }

    private Optional<String> getClipboardContents() {
        try {
            return Optional.ofNullable(clipboard.getContents(this).getTransferData(DataFlavor.stringFlavor).toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public synchronized void start() {
        if (executor != null) return;
        executor = getExecutor();
        onStart.run();
    }

    public synchronized void stop() {
        if (executor == null) return;
        executor.shutdownNow();
        executor = null;
    }
}
