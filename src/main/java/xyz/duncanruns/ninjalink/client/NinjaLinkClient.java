package xyz.duncanruns.ninjalink.client;

import com.formdev.flatlaf.FlatDarkLaf;
import org.jetbrains.annotations.NotNull;
import xyz.duncanruns.ninjalink.client.gui.NinjaLinkGUI;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class NinjaLinkClient {
    private static NinjabrainBotEventData myData = NinjabrainBotEventData.empty();
    private static NinjaLinkGUI ninjaLinkGUI = null;
    private static Socket socket;
    private static NinjabrainBotConnector ninjabrainBot;
    private static boolean closing = false;
    private static NinjaLinkConfig ninjaLinkConfig;

    private NinjaLinkClient() {
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        ninjaLinkConfig = new NinjaLinkConfig();
        if (NinjaLinkConfig.configFileExists()) {
            Optional<NinjaLinkConfig> ninjaLinkConfigOpt = NinjaLinkConfig.tryLoad();
            if (ninjaLinkConfigOpt.isPresent()) ninjaLinkConfig = ninjaLinkConfigOpt.get();
            else System.out.println("Failed to load config!");
        }

        if (Arrays.asList(args).stream().skip(2).noneMatch(s -> s.toLowerCase().contains("nogui")))
            SwingUtilities.invokeAndWait(() -> {
                FlatDarkLaf.setup();
                Font defaultFont = UIManager.getFont("defaultFont");
                float newSize = defaultFont.getSize() * 1.5f;
                UIManager.put("defaultFont", defaultFont.deriveFont(newSize));
                ninjaLinkGUI = new NinjaLinkGUI(NinjaLinkClient::close, getKeyListener());
                ninjaLinkGUI.setVisible(true);
                ninjaLinkGUI.setPinned(ninjaLinkConfig.guiPinned);
            });

        AtomicReference<String> toConnectToRef = new AtomicReference<>(ninjaLinkConfig.ip);
        AtomicReference<String> nameRef = new AtomicReference<>(ninjaLinkConfig.nickname);
        if (ninjaLinkGUI != null && args.length == 0) {
            NinjaLinkConfig finalNinjaLinkConfig = ninjaLinkConfig;
            SwingUtilities.invokeAndWait(() -> {
                toConnectToRef.set(JOptionPane.showInputDialog(ninjaLinkGUI, "Input a NinjaLink server IP and port (optionally):", finalNinjaLinkConfig.ip));
                if (toConnectToRef.get() == null || toConnectToRef.get().isEmpty()) return;
                nameRef.set(JOptionPane.showInputDialog(ninjaLinkGUI, "Enter a nickname:", finalNinjaLinkConfig.nickname));
            });
        } else if (args.length > 0) {
            toConnectToRef.set(args[0]);
            nameRef.set(args[1]);
        }

        String toConnectTo = toConnectToRef.get();
        if (toConnectTo == null || toConnectTo.isEmpty()) {
            close();
            return;
        }

        String ip = toConnectTo;
        int port = 52534;
        if (ip.contains(":")) {
            String[] split = toConnectTo.split(":");
            port = Integer.parseInt(split[1]);
            ip = split[0];
        }

        String name = nameRef.get();
        if (name == null) {
            close();
            return;
        }
        name = name.trim();
        if (name.isEmpty()) {
            close();
            return;
        }

        ninjaLinkConfig.ip = toConnectTo;
        ninjaLinkConfig.nickname = name;
        if (!ninjaLinkConfig.trySave()) System.out.println("Failed to save config!");

        try {
            runClient(ip, port, name);
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
                if (e.getKeyChar() == 'p') {
                    swapGuiPinned();
                }
            }
        };
    }

    private static void swapGuiPinned() {
        ninjaLinkGUI.setPinned(ninjaLinkConfig.guiPinned = !ninjaLinkConfig.guiPinned);
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
        }
        if (ninjabrainBot != null) ninjabrainBot.close();
        SocketUtil.carelesslyClose(socket);
    }

    private static void onNBotConnectionStateChange(NinjabrainBotConnector.ConnectionState previousState, NinjabrainBotConnector.ConnectionState connectionState) {
        if (ninjaLinkGUI != null && connectionState != NinjabrainBotConnector.ConnectionState.CONNECTING)
            ninjaLinkGUI.setNinjabrainBotConnectionState(connectionState);
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

    private static void runClient(String ip, int port, String name) throws IOException {
        try {
            socket = new Socket(ip, port);
        } catch (Exception e) {
            if (closing) return;
            System.out.println("Failed to connect to server!");
            close("Failed to connect to server!");
            return;
        }

        SocketUtil.sendStringWithLength(socket, name);
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

    private static void onNewGroupData(NinjaLinkGroupData ninjaLinkGroupData) {
        System.out.println("New group data received: " + ninjaLinkGroupData.toJson());
        if (ninjaLinkGUI != null) ninjaLinkGUI.setData(ninjaLinkGroupData, myData);
    }
}
