package xyz.duncanruns.ninjalink.server;

import xyz.duncanruns.ninjalink.data.NinjaLinkGroupData;
import xyz.duncanruns.ninjalink.data.NinjabrainBotEventData;
import xyz.duncanruns.ninjalink.data.PlayerData;
import xyz.duncanruns.ninjalink.util.SocketUtil;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static xyz.duncanruns.ninjalink.server.NinjaLinkServer.rejectConnection;

public class Room {
    private final String name;
    private final String password;

    private final Map<String, Socket> userMap = new ConcurrentHashMap<>();
    private final NinjaLinkGroupData groupData = new NinjaLinkGroupData();

    public Room() {
        this("", "");
    }

    public Room(String name, String password) {
        this.name = name;
        this.password = password;
    }


    private void onClientError(Socket client, String msg, Exception e, String name) {
        if (userMap.containsValue(client)) {
            System.out.println(msg);
            e.printStackTrace();
            removeUser(name, client);
        }
    }

    private synchronized void onNewNinjabrainBotEventData(String name, NinjabrainBotEventData ninjabrainBotEventData) {
        PlayerData playerData = PlayerData.fromNinjabrainBotEventData(ninjabrainBotEventData);
        if (playerData.isEmpty())
            groupData.playerDataMap.remove(name);
        else
            groupData.playerDataMap.put(name, playerData);
        updateAllUsers();
    }

    private synchronized void removeUser(String name, Socket client) {
        SocketUtil.carelesslyClose(userMap.remove(name));
        SocketUtil.carelesslyClose(client);
        groupData.playerDataMap.remove(name);
        updateAllUsers();
    }

    private synchronized void updateAllUsers() {
        userMap.forEach(this::sendGroupDataToClient);
    }

    private boolean sendGroupDataToClient(String name, Socket client) {
        try {
            SocketUtil.sendStringWithLength(client, groupData.toJson());
            return true;
        } catch (Exception e) {
            onClientError(client, "Failed to communicate: " + e, e, name);
            return false;
        }
    }

    private void userReceiveLoop(String name, Socket client) {
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
                System.out.println("User " + name + " disconnected from room " + getPrintedRoomName());
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

    public synchronized AcceptType checkAccept(Socket client, String nickname, String roomName, String roomPass) throws IOException {
        if (!(this.name.isEmpty() || roomName.equals(this.name))) {
            return AcceptType.WRONG_ROOM;
        }
        if (!(this.password.isEmpty() || roomPass.equals(this.password))) {
            rejectConnection(client, "Wrong password!");
            return AcceptType.REJECTED;
        }

        if (userMap.containsKey(nickname)) {
            rejectConnection(client, "A user with that name is already connected!");
            return AcceptType.REJECTED;
        }
        SocketUtil.sendStringWithLength(client, "A"); // Accepted
        SocketUtil.sendStringWithLength(client, !roomName.isEmpty() && this.name.isEmpty() ? "Warning: this server does not use custom rooms, anyone using this address will be put into the same room. To skip this message next time, leave the room name and password blank." : ""); // Displayed Server Message


        userMap.put(nickname, client);
        if (!sendGroupDataToClient(nickname, client)) return AcceptType.FAILED;
        System.out.println("User " + nickname + " joined room " + getPrintedRoomName());
        new Thread(() -> userReceiveLoop(nickname, client)).start();
        return AcceptType.ACCEPTED;
    }

    private String getPrintedRoomName() {
        return this.name.isEmpty() ? "default" : this.name;
    }

    public boolean isEmpty() {
        return userMap.isEmpty();
    }

    public enum AcceptType {
        WRONG_ROOM,
        ACCEPTED,
        REJECTED,
        FAILED
    }
}
