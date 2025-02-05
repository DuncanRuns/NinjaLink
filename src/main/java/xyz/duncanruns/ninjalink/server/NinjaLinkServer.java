package xyz.duncanruns.ninjalink.server;

import xyz.duncanruns.ninjalink.data.NinjaLinkGroupData;
import xyz.duncanruns.ninjalink.data.NinjabrainBotEventData;
import xyz.duncanruns.ninjalink.data.PlayerData;
import xyz.duncanruns.ninjalink.util.SocketUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NinjaLinkServer {

    private static final Map<String, Socket> userMap = new ConcurrentHashMap<>();
    private static final NinjaLinkGroupData groupData = new NinjaLinkGroupData();

    private NinjaLinkServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = 52534;
        if (args.length > 0) port = Integer.parseInt(args[0]);

        if (!SocketUtil.isPortFree(port)) {
            System.out.printf("Port %d is already in use!\n", port);
        }

        ServerSocket serverSocket = new ServerSocket(port);

        while (true) {
            acceptNewClient(serverSocket.accept());
        }
    }

    private static synchronized void acceptNewClient(Socket client) {
        try {
            String nickname = SocketUtil.receiveStringWithLength(client);
            if (nickname == null) {
                SocketUtil.carelesslyClose(client);
                return;
            }
            String roomName = SocketUtil.receiveStringWithLength(client);
            if (roomName == null) {
                SocketUtil.carelesslyClose(client);
                return;
            }
            String roomPass = SocketUtil.receiveStringWithLength(client);
            if (roomPass == null) {
                SocketUtil.carelesslyClose(client);
                return;
            }

            if (!roomName.trim().isEmpty()) {
                SocketUtil.sendStringWithLength(client, "R"); // Rejected
                SocketUtil.sendStringWithLength(client, "This server does not support rooms!");
                SocketUtil.carelesslyClose(client);
                return;
            }

            if (nickname.trim().isEmpty()) {
                SocketUtil.sendStringWithLength(client, "R"); // Rejected
                SocketUtil.sendStringWithLength(client, "Name cannot be empty!");
                SocketUtil.carelesslyClose(client);
                return;

            } else if (userMap.containsKey(nickname)) {
                SocketUtil.sendStringWithLength(client, "R"); // Rejected
                SocketUtil.sendStringWithLength(client, "A user with that name is already connected!");
                SocketUtil.carelesslyClose(client);
                return;
            }
            SocketUtil.sendStringWithLength(client, "A"); // Accepted

            userMap.put(nickname, client);
            if (!sendGroupDataToClient(nickname, client)) return;
            new Thread(() -> userReceiveLoop(nickname, client)).start();

        } catch (Exception e) {
            System.out.println("Failed to accept " + client + " due to exception: " + e);
            e.printStackTrace();
        }
    }

    private static void userReceiveLoop(String name, Socket client) {
        while (true) {
            String data;
            try {
                data = SocketUtil.receiveStringWithLength(client);
                if (data == null) throw new IOException("No string received");
            } catch (IOException e) {
                onClientError(client, "Removing user " + name + " due to error: " + e, e, name);
                return;
            }

            if (data.equals("D" /*Disconnect*/)) {
                removeUser(name, client);
                return;
            }

            try {
                onNewNinjabrainBotEventData(name, NinjabrainBotEventData.fromJson(data));
            } catch (Exception e) {
                onClientError(client, "Received invalid data from user " + name + ", removing from server...", e, name);
                return;
            }
        }
    }

    private static void onClientError(Socket client, String msg, Exception e, String name) {
        if (userMap.containsValue(client)) {
            System.out.println(msg);
            e.printStackTrace();
            removeUser(name, client);
        }
    }

    private static synchronized void onNewNinjabrainBotEventData(String name, NinjabrainBotEventData ninjabrainBotEventData) {
        PlayerData playerData = PlayerData.fromNinjabrainBotEventData(ninjabrainBotEventData);
        if (playerData.isEmpty())
            groupData.playerDataMap.remove(name);
        else
            groupData.playerDataMap.put(name, playerData);
        updateAllUsers();
    }

    private static synchronized void updateAllUsers() {
        userMap.forEach(NinjaLinkServer::sendGroupDataToClient);
    }

    private static boolean sendGroupDataToClient(String name, Socket client) {
        try {
            SocketUtil.sendStringWithLength(client, groupData.toJson());
            return true;
        } catch (Exception e) {
            onClientError(client, "Failed to communicate: " + e, e, name);
            return false;
        }
    }

    private static synchronized void removeUser(String name, Socket client) {
        SocketUtil.carelesslyClose(userMap.remove(name));
        SocketUtil.carelesslyClose(client);
        groupData.playerDataMap.remove(name);
        updateAllUsers();
    }
}
