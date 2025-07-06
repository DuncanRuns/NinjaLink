package xyz.duncanruns.ninjalink.server;

import org.java_websocket.WebSocket;
import xyz.duncanruns.ninjalink.data.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static xyz.duncanruns.ninjalink.server.NinjaLinkServer.rejectConnection;

public class Room {
    private final String name;
    private final String password;

    private final Set<WebSocket> watchers = new HashSet<>();
    private final Map<String, WebSocket> userMap = new ConcurrentHashMap<>();
    private final NinjaLinkGroupData groupData = new NinjaLinkGroupData();
    private boolean closed = false;

    public Room() {
        this("", "");
    }

    public Room(String name, String password) {
        this.name = name;
        this.password = password;
    }

    private static void sendDisconnect(WebSocket socket, String message) {
        socket.send(new ServerData(ServerData.Type.DISCONNECT, message).toJson());
    }

    private synchronized void onClientError(WebSocket client, String msg, Exception e, String name) {
        if (userMap.containsValue(client)) {
            System.out.println(msg);
            e.printStackTrace();
            removeUser(name, client);
        }
    }

    private synchronized void onNewNinjabrainBotEventData(String name, NinjabrainBotEventData ninjabrainBotEventData) {
        PlayerData playerData = PlayerData.fromNinjabrainBotEventData(ninjabrainBotEventData);
        if (playerData.isEmpty())
            clearPlayerDataForUser(name);
        else
            groupData.playerDataMap.put(name, playerData);
        updateAllUsers();
    }

    public synchronized void removeUser(WebSocket client) {
        userMap.entrySet().stream().filter(e -> e.getValue() == client).findAny().ifPresent(e -> removeUser(e.getKey(), client));
    }

    private synchronized void removeUser(String name, WebSocket client) {
        System.out.println("Removed user " + name + " from room " + this.getPrintedRoomName());
        userMap.remove(name).close();
        client.close();
        clearPlayerDataForUser(name);
        updateAllUsers();
        checkShouldClose();
    }

    private synchronized void checkShouldClose() {
        if (closed) return;
        if (userMap.isEmpty() && watchers.isEmpty()) close();
    }

    private synchronized void updateAllUsers() {
        userMap.forEach(this::sendGroupDataToClient);
        watchers.forEach(this::sendGroupDataToWatcher);
    }

    private boolean sendGroupDataToClient(String name, WebSocket client) {
        try {
            client.send(new ServerData(groupData).toJson());
            return true;
        } catch (Exception e) {
            onClientError(client, "Failed to communicate: " + e, e, name);
            return false;
        }
    }

    private boolean sendGroupDataToWatcher(WebSocket client) {
        try {
            client.send(new ServerData(groupData).toJson());
            return true;
        } catch (Exception e) {
            removeWatcher(client);
            return false;
        }
    }

    private void removeWatcher(WebSocket client) {
        client.close();
        watchers.remove(client);
        checkShouldClose();
    }

    public void onReceiveString(WebSocket client, String string) throws IOException {
        if (userMap.containsValue(client)) {
            String name = userMap.entrySet().stream().filter(e -> e.getValue() == client).map(Map.Entry::getKey).findAny().orElse(null);
            if (name == null) throw new IllegalStateException("User is in userMap but name is null");
            onReceiveString(name, client, string);
        } else if (watchers.contains(client)) {
            onReceiveWatcherString(client, string);
        } else {
            throw new IllegalStateException("Client is not in userMap or watchers");
        }

    }

    public void onReceiveString(String name, WebSocket client, String string) throws IOException {
        ClientData data = ClientData.fromJson(string);
        if (data == null) throw new IOException("Failed to communicate with client.");

        if (data.type == ClientData.Type.DISCONNECT) {
            System.out.println("User " + name + " disconnected from room " + getPrintedRoomName());
            removeUser(name, client);
        } else if (data.type == ClientData.Type.CLEAR) {
            clearPlayerDataForUser(name);
        } else if (data.type == ClientData.Type.NINJABRAIN_BOT_EVENT_DATA) {
            if (data.ninjabrainBotEventData == null)
                throw new IOException("Expected Ninjabrain Bot Event Data but got null!");
            onNewNinjabrainBotEventData(name, data.ninjabrainBotEventData);
        }
    }

    private synchronized void clearPlayerDataForUser(String name) {
        groupData.playerDataMap.remove(name);
    }

    private void onReceiveWatcherString(WebSocket client, String string) {
        ClientData data;
        data = ClientData.fromJson(string);
        if (data == null || data.type == ClientData.Type.DISCONNECT) {
            removeWatcher(client);
        }
    }

    public synchronized AcceptType checkAccept(WebSocket client, JoinRequest request) {
        if (closed) throw new IllegalStateException("Connection attempt to a closed room.");

        if (!(this.name.isEmpty() || request.roomName.equalsIgnoreCase(this.name))) {
            return AcceptType.WRONG_ROOM;
        }
        if (!(this.password.isEmpty() || request.roomPass.equals(this.password))) {
            rejectConnection(client, "Wrong password!");
            return AcceptType.REJECTED;
        }

        if (!request.isWatcher() && userMap.containsKey(request.nickname)) {
            rejectConnection(client, "A user with that name is already connected!");
            return AcceptType.REJECTED;
        }

        client.send(new JoinRequestResponse(
                true,
                !request.roomName.isEmpty() && this.name.isEmpty() ? "Warning: this server does not use custom rooms, anyone using this address will be put into the same room. To skip this message next time, leave the room name and password blank." : ""
        ).toJson());

        if (request.isWatcher()) {
            watchers.add(client);
            if (!sendGroupDataToWatcher(client)) {
                removeWatcher(client);
                return AcceptType.FAILED;
            }
            System.out.println("A watcher joined room " + getPrintedRoomName());
        } else {
            userMap.put(request.nickname, client);
            if (!sendGroupDataToClient(request.nickname, client)) {
                removeUser(request.nickname, client);
                return AcceptType.FAILED;
            }
            System.out.println("User " + request.nickname + " joined room " + getPrintedRoomName());
        }
        return AcceptType.ACCEPTED;
    }

    private String getPrintedRoomName() {
        return this.name.isEmpty() ? "default" : this.name;
    }

    public boolean isEmpty() {
        return userMap.isEmpty() && watchers.isEmpty();
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        System.out.println("Room " + this.getPrintedRoomName() + " closing...");
        Stream.concat(userMap.values().stream(), watchers.stream()).forEach(socket -> {
            sendDisconnect(socket, "Room or server has been closed.");
            socket.close();
        });
        userMap.clear();
        watchers.clear();
    }

    public synchronized boolean tryKick(String nickname) {
        nickname = userMap.keySet().stream().filter(nickname::equalsIgnoreCase).findAny().orElse(nickname);
        WebSocket socket = userMap.getOrDefault(nickname, null);
        if (socket == null) return false;
        sendDisconnect(socket, "You were kicked from the server!");
        removeUser(nickname, socket);
        return true;
    }

    public String getName() {
        return name;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(this.getPrintedRoomName());
        if (userMap.isEmpty() && watchers.isEmpty()) out.append(" (empty)");
        userMap.keySet().forEach(name -> out.append("\n- ").append(name));
        if (!watchers.isEmpty()) out.append("\n- ").append(watchers.size()).append(" Watchers");
        return out.toString();
    }

    public enum AcceptType {
        WRONG_ROOM,
        ACCEPTED,
        REJECTED,
        FAILED
    }
}
