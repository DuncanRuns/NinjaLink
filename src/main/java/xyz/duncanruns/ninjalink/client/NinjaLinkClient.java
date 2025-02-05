package xyz.duncanruns.ninjalink.client;

import com.formdev.flatlaf.FlatDarkLaf;
import org.jetbrains.annotations.NotNull;
import xyz.duncanruns.ninjalink.client.gui.NinjaLinkGUI;
import xyz.duncanruns.ninjalink.client.gui.NinjaLinkPrompt;
import xyz.duncanruns.ninjalink.data.NinjaLinkGroupData;
import xyz.duncanruns.ninjalink.data.NinjabrainBotEventData;
import xyz.duncanruns.ninjalink.util.SocketUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

public final class NinjaLinkClient {
    private static NinjabrainBotEventData myData = NinjabrainBotEventData.empty();
    private static NinjaLinkGUI ninjaLinkGUI = null;
    private static Socket socket;
    private static NinjabrainBotConnector ninjabrainBot;
    private static boolean closing = false;
    private static NinjaLinkConfig ninjaLinkConfig;
    private static NinjaLinkGroupData latestData = new NinjaLinkGroupData();

    private NinjaLinkClient() {
    }

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
                close();
                return;
            }
            ip = prompt.getAddress();
            nickname = prompt.getNickname();
            roomName = prompt.getRoomName();
            roomPass = prompt.getRoomPass();
        }

        ninjaLinkConfig.ip = ip.trim();

        int port = 52534;
        if (ip.contains(":")) {
            String[] split = ip.split(":");
            try {
                port = Integer.parseInt(split[1]);
            } catch (Exception e) {
                close("Failed to convert port to a number!");
                return;
            }
            ip = split[0];
        }

        ninjaLinkConfig.nickname = nickname.trim();
        ninjaLinkConfig.roomName = roomName.trim();
        ninjaLinkConfig.roomPass = roomPass.trim();
        trySaveConfig();

        try {
            runClient(ip, port);
        } catch (Exception e) {
            System.out.println("Error while trying to run client: " + e);
            if (ninjaLinkGUI != null)
                SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(ninjaLinkGUI, "Failed to run client: " + e, "NinjaLink: Failed", JOptionPane.ERROR_MESSAGE));
            e.printStackTrace();
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
                    case 80:
                        swapGuiPinned();
                        break;
                }
            }
        };
    }

    private static void bumpFontSize(boolean up) {
        int currentSize = ninjaLinkConfig.fontSize;
        int newSize = Math.min(24, Math.max(4, currentSize + (up ? 2 : -2)));
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
        ninjaLinkGUI = new NinjaLinkGUI(NinjaLinkClient::close, getKeyListener());
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

    private static synchronized void close() {
        close(null);
    }

    private static synchronized void close(String closeMessage) {
        if (closing) return;
        closing = true;
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
        if (socket != null && socket.isConnected() && !socket.isClosed()) sendCarelessDisconnect(socket);
        SocketUtil.carelesslyClose(socket);
        if (ninjaLinkGUI != null) ninjaLinkConfig.bounds = ninjaLinkGUI.getBounds();
        trySaveConfig();
    }

    private static void sendCarelessDisconnect(Socket socket) {
        try {
            SocketUtil.sendStringWithLength(socket, "D");
        } catch (Exception ignored) {
        }
    }

    private static void onNBotConnectionStateChange(NinjabrainBotConnector.ConnectionState previousState, NinjabrainBotConnector.ConnectionState connectionState) {
        if (ninjaLinkGUI != null) ninjaLinkGUI.setNinjabrainBotConnectionState(connectionState);
        if (previousState == NinjabrainBotConnector.ConnectionState.CONNECTED) {
            System.out.println("Disconnected from Ninjabrain Bot");
            try {
                System.out.println("Sending empty data to server...");
                myData = NinjabrainBotEventData.empty();
                SocketUtil.sendStringWithLength(socket, myData.toJson());
            } catch (Exception e) {
                if (closing) return;
                System.out.println("Error sending ninjabrain bot data: " + e);
                e.printStackTrace();
                close("Error sending ninjabrain bot data: " + e);
            }
        } else if (connectionState == NinjabrainBotConnector.ConnectionState.CONNECTED) {
            System.out.println("Connected to Ninjabrain Bot");
        }
    }

    private static synchronized void onNBotEvent(String string) {
        System.out.println("New Ninjabrain Bot data received!");
        if (socket.isClosed() || !socket.isConnected()) return;
        try {
            myData = NinjabrainBotEventData.fromJson(string);
            System.out.println("Sending to server...");
            SocketUtil.sendStringWithLength(socket, string);
        } catch (Exception e) {
            if (closing) return;
            System.out.println("Error sending ninjabrain bot data: " + e);
            e.printStackTrace();
            close("Error sending ninjabrain bot data: " + e);
        }
    }

    private static void runClient(String ip, int port) throws IOException {
        try {
            socket = new Socket(ip, port);
        } catch (Exception e) {
            if (closing) return;
            System.out.println("Failed to connect to server!");
            close("Failed to connect to server!");
            return;
        }

        SocketUtil.sendStringWithLength(socket, ninjaLinkConfig.nickname);
        SocketUtil.sendStringWithLength(socket, ninjaLinkConfig.roomName);
        SocketUtil.sendStringWithLength(socket, ninjaLinkConfig.roomPass);
        String response = SocketUtil.receiveStringWithLength(socket);
        if (response == null) {
            if (closing) return;
            System.out.println("Failed to communicate with server.");
            close("Failed to communicate with server.");
            return;
        }
        if (response.equals("R")) { // Rejected
            response = SocketUtil.receiveStringWithLength(socket);
            System.out.println("Rejected for reason: " + response);
            if (response == null) {
                if (closing) return;
                System.out.println("Failed to communicate with server.");
                close("Rejected connection, then failed to communicate with server.");
            }
            close("Rejected for reason: " + response);
            return;
        } else if (!response.equals("A")) { // Something that isn't accepted !?
            if (closing) return;
            System.out.println("Unexpected response: '" + response + "'");
            close("Unexpected response: '" + response + "'");
            return;
        }

        System.out.println("Connection accepted!");

        ninjabrainBot = new NinjabrainBotConnector(NinjaLinkClient::onNBotEvent, NinjaLinkClient::onNBotConnectionStateChange);

        ninjabrainBot.start();
        receiveLoop(socket);
    }

    private static void receiveLoop(Socket socket) {
        while (true) {
            String data;
            try {
                data = SocketUtil.receiveStringWithLength(socket);
                if (data == null) throw new IOException("No string received from ");
            } catch (IOException e) {
                if (closing) return;
                System.out.println("Error during communication: " + e);
                e.printStackTrace();
                close("Error during communication: " + e);
                return;
            }

            if (data.equals("D" /*Disconnect*/)) {
                System.out.println("Received disconnect request.");
                close("Disconnected from server.");
                return;
            }

            try {
                onNewGroupData(NinjaLinkGroupData.fromJson(data));
            } catch (Exception e) {
                if (closing) return;
                System.out.println("Invalid data: " + e);
                e.printStackTrace();
                close("Invalid data: " + e);
                return;
            }
        }
    }

    private static synchronized void onNewGroupData(NinjaLinkGroupData ninjaLinkGroupData) {
        System.out.println("New group data received: " + ninjaLinkGroupData.toJson());
        NinjaLinkClient.latestData = ninjaLinkGroupData;
        if (ninjaLinkGUI != null) ninjaLinkGUI.setData(ninjaLinkGroupData, myData);
    }
}
