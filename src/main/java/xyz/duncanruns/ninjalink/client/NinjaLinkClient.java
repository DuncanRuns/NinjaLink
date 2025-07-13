package xyz.duncanruns.ninjalink.client;

import com.formdev.flatlaf.FlatDarkLaf;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import xyz.duncanruns.ninjalink.Constants;
import xyz.duncanruns.ninjalink.client.clipboard.ClipboardListener;
import xyz.duncanruns.ninjalink.client.gui.NinjaLinkGUI;
import xyz.duncanruns.ninjalink.client.gui.NinjaLinkPrompt;
import xyz.duncanruns.ninjalink.data.*;
import xyz.duncanruns.ninjalink.data.Dimension;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NinjaLinkClient {
    private static NinjaLinkConfig ninjaLinkConfig;
    private static NinjaLinkGUI ninjaLinkGUI = null;

    private static NinjabrainBotConnector ninjabrainBot;
    private static PlayerData myData = PlayerData.empty();
    private static NinjaLinkGroupData latestData = new NinjaLinkGroupData();
    private static boolean closing = false;
    private static boolean joined = false;
    private static NinjaLinkWSC ws;
    private static final ClipboardListener clipboardListener = new ClipboardListener(250, NinjaLinkClient::onClipboardChange);

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        ninjaLinkConfig = new NinjaLinkConfig();
        if (NinjaLinkConfig.configFileExists()) {
            Optional<NinjaLinkConfig> ninjaLinkConfigOpt = NinjaLinkConfig.tryLoad();
            if (ninjaLinkConfigOpt.isPresent()) ninjaLinkConfig = ninjaLinkConfigOpt.get();
            else System.out.println("Failed to load config!");
        }

        Iterator<String> argIter = Arrays.stream(args).iterator();

        String ip = argIter.hasNext() ? argIter.next() : "";
        String nickname = argIter.hasNext() ? argIter.next() : "";
        String roomName = argIter.hasNext() ? argIter.next() : "";
        String roomPass = argIter.hasNext() ? argIter.next() : "";
        if (!(argIter.hasNext() && argIter.next().toLowerCase().contains("nogui"))) SwingUtilities.invokeAndWait(() -> {
            FlatDarkLaf.setup();
            createGUIWithFontSize(ninjaLinkConfig.fontSize);
        });


        if (ninjaLinkGUI != null && nickname.isEmpty()) {
            NinjaLinkPrompt prompt = new NinjaLinkPrompt(ninjaLinkGUI, ninjaLinkConfig);
            prompt.setVisible(true);
            if (!prompt.hasPressedOk()) {
                closeClient();
                return;
            }
            ip = prompt.getAddress();
            nickname = prompt.getNickname();
            roomName = prompt.getRoomName();
            roomPass = prompt.getRoomPass();
        }

        ninjaLinkConfig.address = ip.trim();
        ninjaLinkConfig.nickname = nickname.trim();
        ninjaLinkConfig.roomName = roomName.trim();
        ninjaLinkConfig.roomPass = roomPass.trim();
        trySaveConfig();

        try {
            if (ip.isEmpty()) ip = Constants.DEFAULT_ADDRESS;
            System.out.println("Connecting to " + ip + "...");
            ws = new NinjaLinkWSC(URI.create(ip));
            ws.setDaemon(true);
            ws.connectBlocking();
        } catch (Exception e) {
            System.out.println("Error while trying to run client: " + e);
            if (ninjaLinkGUI != null)
                SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(ninjaLinkGUI, "Failed to run client: " + e, "NinjaLink: Failed", JOptionPane.ERROR_MESSAGE));
            e.printStackTrace();
            System.exit(0);
        }
    }

    @NotNull
    private static KeyListener getKeyListener() {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case 38:
                        bumpFontSize(true);
                        break;
                    case 40:
                        bumpFontSize(false);
                        break;
                    case 67:
                        onClearPressed();
                        break;
                    case 80:
                        swapGuiPinned();
                        break;
                }
            }
        };
    }

    private static synchronized void onClearPressed() {
        if (ninjabrainBot.getConnectionState() == NinjabrainBotConnector.ConnectionState.CONNECTED) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ninjaLinkGUI, "To clear position while connected to Ninjabrain Bot, press your Ninjabrain Bot reset key or click the reset button on the Ninjabrain Bot GUI.", "NinjaLink: Cannot Clear", JOptionPane.INFORMATION_MESSAGE));
            return;
        }
        myData = PlayerData.empty();
        sendClear();
    }

    private static void bumpFontSize(boolean up) {
        int currentSize = ninjaLinkConfig.fontSize;
        int newSize = Math.min(24, Math.max(8, currentSize + (up ? 2 : -2)));
        if (currentSize == newSize) return;
        System.out.println("Changed font size to " + newSize);
        createGUIWithFontSize(newSize);

        ninjaLinkConfig.fontSize = newSize;
        trySaveConfig();
    }

    private static synchronized void createGUIWithFontSize(int size) {
        Font defaultFont = UIManager.getFont("defaultFont");
        UIManager.put("defaultFont", defaultFont.deriveFont((float) size));
        UIManager.put("Table.foreground", Color.WHITE);
        if (ninjaLinkGUI != null) {
            ninjaLinkGUI.discard();
            ninjaLinkConfig.bounds = ninjaLinkGUI.getBounds();
        }
        ninjaLinkGUI = new NinjaLinkGUI(NinjaLinkClient::closeClient, getKeyListener());
        ninjaLinkGUI.setData(latestData, myData);
        ninjaLinkGUI.setBounds(ninjaLinkConfig.bounds);
        ninjaLinkGUI.setPinned(ninjaLinkConfig.guiPinned);
        ninjaLinkGUI.adjustSize();
        ninjaLinkGUI.setVisible(true);
        ninjaLinkGUI.requestFocus();
    }

    private static void swapGuiPinned() {
        ninjaLinkGUI.setPinned(ninjaLinkConfig.guiPinned = !ninjaLinkConfig.guiPinned);
        trySaveConfig();
    }

    private static void trySaveConfig() {
        if (!ninjaLinkConfig.trySave()) System.out.println("Failed to save config!");
    }

    private static synchronized void closeClient() {
        closeClient(null);
    }

    private static synchronized void closeClient(String closeMessage) {
        if (closing) return;
        closing = true;
        if (ws != null) ws.close();
        if (ninjaLinkGUI != null) {
            SwingUtilities.invokeLater(() -> {
                if (closeMessage != null)
                    JOptionPane.showMessageDialog(ninjaLinkGUI, closeMessage, "NinjaLink Closing...", JOptionPane.WARNING_MESSAGE);
                ninjaLinkGUI.dispose();
            });
        } else {
            System.out.println(closeMessage);
        }
        if (ninjabrainBot != null) ninjabrainBot.close();
        if (ninjaLinkGUI != null) ninjaLinkConfig.bounds = ninjaLinkGUI.getBounds();
        trySaveConfig();
    }

    private static synchronized void onNBotConnectionStateChange(NinjabrainBotConnector.ConnectionState previousState, NinjabrainBotConnector.ConnectionState connectionState) {
        if (connectionState != NinjabrainBotConnector.ConnectionState.CONNECTED) {
            clipboardListener.start();
        } else {
            clipboardListener.stop();
        }
        if (ninjaLinkGUI != null) ninjaLinkGUI.setNinjabrainBotConnectionState(connectionState);
        if (previousState == NinjabrainBotConnector.ConnectionState.CONNECTED) {
            System.out.println("Disconnected from Ninjabrain Bot");
            if (closing) return;
            try {
                System.out.println("Sending empty data to server...");
                myData = PlayerData.empty();
                sendClear();
            } catch (Exception e) {
                if (closing) return;
                System.out.println("Error sending ninjabrain bot data: " + e);
                e.printStackTrace();
                closeClient("Error sending ninjabrain bot data: " + e);
            }
        } else if (connectionState == NinjabrainBotConnector.ConnectionState.CONNECTED) {
            System.out.println("Connected to Ninjabrain Bot");
        }
    }

    private static synchronized void onNBotEvent(String string) {
        System.out.println("New Ninjabrain Bot data received!");
        if (!ws.isOpen()) return;
        try {
            NinjabrainBotEventData data = NinjabrainBotEventData.fromJson(string);
            myData = PlayerData.fromNinjabrainBotEventData(data);
            System.out.println("Sending to server...");
            ws.send(new ClientData(data).toJson());
        } catch (Exception e) {
            if (closing) return;
            System.out.println("Error sending ninjabrain bot data: " + e);
            e.printStackTrace();
            closeClient("Error sending ninjabrain bot data: " + e);
        }
    }

    private static void onJoinResponseString(String responseStr) {
        if (responseStr == null || responseStr.isEmpty()) {
            if (closing) return;
            closeClient("Failed to communicate with server.");
        }
        JoinRequestResponse response;
        try {
            response = JoinRequestResponse.fromJson(responseStr);
            if (response == null) throw new IOException("Empty response.");
        } catch (Exception e) {
            if (closing) return;
            closeClient("Failed to parse server response.");
            return;
        }

        if (!response.accepted) {
            closeClient("Rejected for reason: " + response.message);
            return;
        }

        if (!response.message.isEmpty()) {
            if (ninjaLinkGUI == null) {
                System.out.println("Message from server:\n" + response.message);
            } else {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ninjaLinkGUI, response.message, "NinjaLink: Server Message", JOptionPane.INFORMATION_MESSAGE));
            }
        }

        System.out.println("Connection accepted!");

        joined = true;

        ninjabrainBot = new NinjabrainBotConnector(NinjaLinkClient::onNBotEvent, NinjaLinkClient::onNBotConnectionStateChange);
        ninjabrainBot.start();
    }

    private static void onReceiveServerDataString(String string) {
        ServerData data;
        try {
            data = ServerData.fromJson(string);
            if (data == null) throw new IOException("No data received from server");
        } catch (Exception e) {
            if (!closing) e.printStackTrace();
            closeClient("Error during communication: " + e);
            return;
        }

        if (data.type == ServerData.Type.DISCONNECT) {
            closeClient("Disconnected from server: " + data.message);
        } else if (data.type == ServerData.Type.GROUP_DATA) {
            if (data.ninjaLinkGroupData == null) {
                closeClient("Expected group data but got null!");
                return;
            }
            onNewGroupData(data.ninjaLinkGroupData);
        }
    }

    private static synchronized void onNewGroupData(NinjaLinkGroupData ninjaLinkGroupData) {
        System.out.println("New group data received: " + ninjaLinkGroupData.toJson());
        NinjaLinkClient.latestData = ninjaLinkGroupData;
        if (ninjaLinkGUI != null) ninjaLinkGUI.setData(ninjaLinkGroupData, myData);
    }

    private static synchronized void onClipboardChange(String s) {
        if (ninjabrainBot.getConnectionState() == NinjabrainBotConnector.ConnectionState.CONNECTED) return;
        Pattern f3cPattern = Pattern.compile("^\\/execute in (\\w+:\\w+) run tp @s (-?\\d+\\.\\d\\d) (-?\\d+\\.\\d\\d) (-?\\d+\\.\\d\\d) (-?\\d+\\.\\d\\d) (-?\\d+\\.\\d\\d)$");
        Matcher matcher = f3cPattern.matcher(s);
        if (!matcher.matches()) return;
        String dimensionStr = matcher.group(1);
        Dimension dimension;
        if (dimensionStr.contains("nether")) {
            dimension = Dimension.NETHER;
        } else if (dimensionStr.contains("end")) {
            dimension = Dimension.END;
        } else {
            dimension = Dimension.OVERWORLD; // Could be some weird dimension like a custom world but that's fine
        }

        // Get (ints) x, y, z, (doubles) yaw, and pitch from matcher
        long x = (long) Math.floor(Double.parseDouble(matcher.group(2)));
        long y = (long) Math.floor(Double.parseDouble(matcher.group(3)));
        long z = (long) Math.floor(Double.parseDouble(matcher.group(4)));
        double yaw = Double.parseDouble(matcher.group(5));
        double pitch = Double.parseDouble(matcher.group(6));

        F3CData f3CData = new F3CData(dimension, x, y, z, yaw, pitch);
        try {
            ws.send(new ClientData(f3CData).toJson());
        } catch (Exception e) {
            if (closing) return;
            System.out.println("Error sending f3c data: " + e);
            e.printStackTrace();
            closeClient("Error sending f3c data: " + e);
        }

    }

    private static synchronized void sendClear() {
        ws.send(new ClientData(ClientData.Type.CLEAR).toJson());
    }

    private static class NinjaLinkWSC extends WebSocketClient {
        public NinjaLinkWSC(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("Connected to server!");
            this.send(new JoinRequest(ninjaLinkConfig.nickname, ninjaLinkConfig.roomName, ninjaLinkConfig.roomPass, Constants.PROTOCOL_VERSION).toJson());
        }

        @Override
        public void onMessage(String message) {
            if (joined) {
                NinjaLinkClient.onReceiveServerDataString(message);
            } else {
                NinjaLinkClient.onJoinResponseString(message);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeClient("Connection closed by " + (remote ? "server" : "client") + " with code " + code + " and reason: " + reason);
        }

        @Override
        public void onError(Exception ex) {
            closeClient("Error in websocket: " + ex + "\n" + ex.getMessage());
        }
    }
}
