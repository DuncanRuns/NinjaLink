package xyz.duncanruns.ninjalink.server;

import xyz.duncanruns.ninjalink.util.SocketUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class NinjaLinkServer {

    private static final Set<Room> rooms = new HashSet<>();
    private static boolean useRooms;

    private NinjaLinkServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = 52534;
        if (args.length > 0) port = Integer.parseInt(args[0].equalsIgnoreCase("default") ? "52534" : args[0]);
        useRooms = args.length > 1 && Arrays.stream(args).skip(1).anyMatch(s -> s.toLowerCase().contains("rooms"));
        if (!useRooms) rooms.add(new Room());

        if (!SocketUtil.isPortFree(port)) {
            System.out.printf("Port %d is already in use!\n", port);
        }

        ServerSocket serverSocket = new ServerSocket(port);

        if (!serverSocket.isBound()) {
            System.out.println("Server socket did not bind!");
            return;
        }

        while (serverSocket.isBound()) {
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
            nickname = nickname.trim();
            String roomName = SocketUtil.receiveStringWithLength(client);
            if (roomName == null) {
                SocketUtil.carelesslyClose(client);
                return;
            }
            roomName = roomName.trim();
            String roomPass = SocketUtil.receiveStringWithLength(client);
            if (roomPass == null) {
                SocketUtil.carelesslyClose(client);
                return;
            }
            roomPass = roomPass.trim();

            if (nickname.trim().isEmpty()) {
                rejectConnection(client, "Name cannot be empty!");
                return;
            }

            if (useRooms) rooms.removeIf(Room::isEmpty);

            for (Room room : rooms) {
                Room.AcceptType acceptType = room.checkAccept(client, nickname, roomName, roomPass);
                if (acceptType != Room.AcceptType.WRONG_ROOM) return;
            }

            if (useRooms && !roomName.isEmpty()) {
                Room room = new Room(roomName, roomPass);
                rooms.add(room);
                Room.AcceptType acceptType = room.checkAccept(client, nickname, roomName, roomPass);
                if (acceptType != Room.AcceptType.ACCEPTED) rooms.remove(room);
                return;
            }

            rejectConnection(client, useRooms ? "This server uses rooms! Please input a room name and (optionally) a password to connect to or create a room." : "Failed to connect: unknown reason (this should not happen)");

        } catch (Exception e) {
            System.out.println("Failed to accept " + client + " due to exception: " + e);
            e.printStackTrace();
        }
    }

    public static void rejectConnection(Socket client, String msg) {
        try {
            SocketUtil.sendStringWithLength(client, "R"); // Rejected
            SocketUtil.sendStringWithLength(client, msg);
        } catch (Exception ignored) {
        }
        SocketUtil.carelesslyClose(client);
    }
}
