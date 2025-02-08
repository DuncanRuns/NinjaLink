package xyz.duncanruns.ninjalink.server;

import xyz.duncanruns.ninjalink.data.*;
import xyz.duncanruns.ninjalink.util.SocketUtil;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static xyz.duncanruns.ninjalink.server.NinjaLinkServer.rejectConnection;

public class Room {
    private final String name;
    private final String password;

    private final Set<Socket> watchers = new HashSet<>();
    private final Map<String, Socket> userMap = new ConcurrentHashMap<>();
    private final Map<Socket, Long> pingMap = new ConcurrentHashMap<>();
    private final NinjaLinkGroupData groupData = new NinjaLinkGroupData();
    private boolean closed = false;

    public Room() {
        this("", "");
    }

    public Room(String name, String password) {
        this.name = name;
        this.password = password;
        Thread checkPingsThread = new Thread(this::checkPingsLoop, "ping-checker-" + getPrintedRoomName());
        checkPingsThread.setDaemon(true);
        checkPingsThread.start();
    }

    private void checkPingsLoop() {
        while (!closed) {
            synchronized (this) {
                long currentTime = System.currentTimeMillis();
                new HashMap<>(pingMap).forEach((socket, lastPing) -> {
                    if (Math.abs(currentTime - lastPing) > 15_000) socketTimedOut(socket);
                });
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private synchronized void socketTimedOut(Socket socket) {
        try {
            SocketUtil.sendStringWithLength(socket, new ServerData(ServerData.Type.DISCONNECT, "No data received in the last 15 seconds.").toJson());
        } catch (IOException ignored) {
        }
        SocketUtil.carelesslyClose(socket);
        if (!userMap.containsValue(socket)) return;
        userMap.entrySet().stream().filter(e -> e.getValue() == socket).forEach(e -> removeUser(e.getKey(), e.getValue()));
    }

    private synchronized void onClientError(Socket client, String msg, Exception e, String name) {
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

    private synchronized void removeUser(String name, Socket client) {
        System.out.println("Removed user " + name);
        SocketUtil.carelesslyClose(userMap.remove(name));
        SocketUtil.carelesslyClose(client);
        pingMap.remove(client);
        clearPlayerDataForUser(name);
        updateAllUsers();
    }

    private synchronized void updateAllUsers() {
        userMap.forEach(this::sendGroupDataToClient);
        watchers.forEach(this::sendGroupDataToWatcher);
    }

    private boolean sendGroupDataToClient(String name, Socket client) {
        try {
            SocketUtil.sendStringWithLength(client, new ServerData(groupData).toJson());
            return true;
        } catch (Exception e) {
            onClientError(client, "Failed to communicate: " + e, e, name);
            return false;
        }
    }

    private boolean sendGroupDataToWatcher(Socket client) {
        try {
            SocketUtil.sendStringWithLength(client, new ServerData(groupData).toJson());
            return true;
        } catch (Exception e) {
            removeWatcher(client);
            return false;
        }
    }

    private void removeWatcher(Socket client) {
        SocketUtil.carelesslyClose(client);
        watchers.remove(client);
    }

    private void userReceiveLoop(String name, Socket client) {
        while (!closed) {
            try {
                ClientData data = ClientData.fromJson(SocketUtil.receiveStringWithLength(client));
                if (data == null) throw new IOException("Failed to communicate with client.");

                System.out.println("Ping!");
                pingMap.put(client, System.currentTimeMillis());

                if (data.type == ClientData.Type.DISCONNECT) {
                    System.out.println("User " + name + " disconnected from room " + getPrintedRoomName());
                    removeUser(name, client);
                    return;
                } else if (data.type == ClientData.Type.CLEAR) {
                    clearPlayerDataForUser(name);
                } else if (data.type == ClientData.Type.NINJABRAIN_BOT_EVENT_DATA) {
                    if (data.ninjabrainBotEventData == null)
                        throw new IOException("Expected Ninjabrain Bot Event Data but got null!");
                    onNewNinjabrainBotEventData(name, data.ninjabrainBotEventData);
                }
            } catch (Exception e) {
                onClientError(client, "Removing user " + name + " due to error: " + e, e, name);
                return;
            }
        }
    }

    private synchronized void clearPlayerDataForUser(String name) {
        groupData.playerDataMap.remove(name);
    }

    private void watcherReceiveLoop(Socket client) {
        while (!closed) {
            String data;
            try {
                data = SocketUtil.receiveStringWithLength(client);
                if (data == null) throw new IOException("No string received");
            } catch (IOException e) {
                removeWatcher(client);
                return;
            }

            if (data.equals("D" /*Disconnect*/)) {
                removeWatcher(client);
                return;
            }
        }
    }

    public synchronized AcceptType checkAccept(Socket client, JoinRequest request) throws IOException {
        if (closed) throw new IllegalStateException("Connection attempt to a closed room.");

        if (!(this.name.isEmpty() || request.roomName.equals(this.name))) {
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

        SocketUtil.sendStringWithLength(client, new JoinRequestResponse(
                true,
                !request.roomName.isEmpty() && this.name.isEmpty() ? "Warning: this server does not use custom rooms, anyone using this address will be put into the same room. To skip this message next time, leave the room name and password blank." : ""
        ).toJson());

        if (request.isWatcher()) {
            watchers.add(client);
            if (!sendGroupDataToWatcher(client)) {
                removeWatcher(client);
                return AcceptType.FAILED;
            }
            new Thread(() -> watcherReceiveLoop(client)).start();
            System.out.println("A watcher joined room " + getPrintedRoomName());
        } else {
            pingMap.put(client, System.currentTimeMillis());
            userMap.put(request.nickname, client);
            if (!sendGroupDataToClient(request.nickname, client)) {
                removeUser(request.nickname, client);
                return AcceptType.FAILED;
            }
            new Thread(() -> userReceiveLoop(request.nickname, client)).start();
            System.out.println("User " + request.nickname + " joined room " + getPrintedRoomName());
        }
        return AcceptType.ACCEPTED;
    }

    private String getPrintedRoomName() {
        return this.name.isEmpty() ? "default" : this.name;
    }

    public boolean isEmpty() {
        return userMap.isEmpty();
    }

    public synchronized void close() {
        closed = true;
        userMap.forEach((s, socket) -> SocketUtil.carelesslyClose(socket));
        userMap.clear();
        watchers.forEach(SocketUtil::carelesslyClose);
        watchers.clear();
    }

    public enum AcceptType {
        WRONG_ROOM,
        ACCEPTED,
        REJECTED,
        FAILED
    }
}
