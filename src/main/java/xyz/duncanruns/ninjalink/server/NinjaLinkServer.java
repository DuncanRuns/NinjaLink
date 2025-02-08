package xyz.duncanruns.ninjalink.server;

import xyz.duncanruns.ninjalink.Constants;
import xyz.duncanruns.ninjalink.data.JoinRequest;
import xyz.duncanruns.ninjalink.data.JoinRequestResponse;
import xyz.duncanruns.ninjalink.util.SocketUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
            String joinRequestStr = SocketUtil.receiveStringWithLength(client);
            if (joinRequestStr == null) throw new IOException("Failed to communicate with new client.");
            JoinRequest joinRequest;
            try {
                joinRequest = JoinRequest.fromJson(joinRequestStr);
            } catch (Exception e) {
                rejectConnection(client, "Invalid data or incorrect version of NinjaLink, please use version " + Constants.VERSION + " or higher.");
                return;
            }

            if (!Constants.ACCEPTED_PROTOCOLS.contains(joinRequest.protocolVersion)) {
                rejectConnection(client, "Invalid protocol version (" + joinRequest.protocolVersion + "), please update NinjaLink to version " + Constants.VERSION + " or higher.");
                return;
            }

            if (!joinRequest.isWatcher() && joinRequest.nickname.trim().isEmpty()) {
                rejectConnection(client, "Name cannot be empty!");
                return;
            }

            if (useRooms)
                rooms.stream().filter(Room::isEmpty).peek(Room::close).collect(Collectors.toList()).forEach(rooms::remove);

            for (Room room : rooms) {
                Room.AcceptType acceptType = room.checkAccept(client, joinRequest);
                if (acceptType != Room.AcceptType.WRONG_ROOM) return;
            }

            String roomName = joinRequest.roomName;
            if (useRooms && !roomName.isEmpty()) {
                Room room = new Room(roomName, joinRequest.roomPass);
                rooms.add(room);
                Room.AcceptType acceptType = room.checkAccept(client, joinRequest);
                if (acceptType != Room.AcceptType.ACCEPTED) rooms.remove(room);
                return;
            }

            rejectConnection(client, useRooms ? "This server uses rooms! Please input a room name and (optionally) a password to connect to or create a room." : "Failed to connect: unknown reason (this should not happen)");

        } catch (Exception e) {
            System.out.println("Failed to accept " + client + " due to exception: " + e);
            rejectConnection(client, "There was an error trying to the connection.");
            e.printStackTrace();
        }
    }

    public static void rejectConnection(Socket client, String msg) {
        try {
            SocketUtil.sendStringWithLength(client, new JoinRequestResponse(false, msg).toJson());
        } catch (Exception ignored) {
        }
        SocketUtil.carelesslyClose(client);
    }
}
