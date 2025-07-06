package xyz.duncanruns.ninjalink.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import xyz.duncanruns.ninjalink.Constants;
import xyz.duncanruns.ninjalink.data.JoinRequest;
import xyz.duncanruns.ninjalink.data.JoinRequestResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class NinjaLinkServer {

    private static final Set<Room> rooms = new HashSet<>();
    private static boolean useRooms;
    private static NinjaLinkWSS ninjaLinkWSS;

    private static final Map<WebSocket, Room> connections = new ConcurrentHashMap<>();

    private NinjaLinkServer() {
    }

    public static void main(String[] args) {
        int port = 52534;
        if (args.length > 0) port = Integer.parseInt(args[0].equalsIgnoreCase("default") ? "52534" : args[0]);
        useRooms = Arrays.stream(args).skip(1).anyMatch("rooms"::equalsIgnoreCase);
        if (!useRooms) rooms.add(new Room());

        System.out.println("Starting websocket on port " + port + "...");
        ninjaLinkWSS = new NinjaLinkWSS(port);
        ninjaLinkWSS.setConnectionLostTimeout(15);
        ninjaLinkWSS.setReuseAddr(true);
        ninjaLinkWSS.start();

        if (System.in != null) {
            Thread inputThread = new Thread(NinjaLinkServer::inputLoop);
            inputThread.setDaemon(true);
            inputThread.start();
        }
    }

    private static void inputLoop() {
        boolean running = true;
        while (running) {
            Scanner scanner = new Scanner(System.in);
            String line = scanner.nextLine();

            if (line.contains("\u0004")) {
                close();
                running = false;
                continue;
            }

            String[] args = line.split("[ \\t]+");

            if (!runCommand(args)) running = false;
        }
    }

    public static synchronized boolean runCommand(String[] args) {
        switch (args[0]) {
            case "list":
                listCommand();
                break;
            case "kick":
                kickCommand(withoutFirstArg(args));
                break;
            case "stop":
                System.out.println("Stopping server...");
                close();
                return false;
            default:
                System.out.println("Invalid command!");
                break;
        }
        return true; // Continue running
    }

    private static synchronized void kickCommand(String[] args) {
        switch (args.length) {
            case 1:
                if (rooms.stream().noneMatch(room -> room.tryKick(args[0]))) {
                    System.out.println("User " + args[0] + " not found!");
                }
                break;
            case 2:
                if (rooms.stream().filter(room -> room.getName().equalsIgnoreCase(args[1])).noneMatch(room -> room.tryKick(args[0]))) {
                    System.out.println("User " + args[0] + " not found!");
                }
                break;
            default:
                System.out.println("Incorrect args! Usage: kick <nickname> or kick <nickname> <room name>");
        }
    }

    private static synchronized void listCommand() {
        clearEmptyOrClosedRooms();
        if (rooms.isEmpty()) System.out.println("No rooms!");
        rooms.forEach(System.out::println);
    }

    private static synchronized void close() {
        rooms.forEach(Room::close);
        try {
            if (ninjaLinkWSS != null) ninjaLinkWSS.stop(5000, "NinjaLink Server closing");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static synchronized void acceptNewClient(WebSocket client, String joinRequestStr) {
        try {
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

            if (!joinRequest.isValid()) {
                rejectConnection(client, "Invalid join request!");
                return;
            }

            if (!joinRequest.isWatcher() && (joinRequest.nickname == null || joinRequest.nickname.trim().isEmpty())) {
                rejectConnection(client, "Name cannot be empty!");
                return;
            }

            clearEmptyOrClosedRooms();

            for (Room room : rooms) {
                Room.AcceptType acceptType = room.checkAccept(client, joinRequest);
                if (acceptType != Room.AcceptType.WRONG_ROOM) {
                    if (acceptType == Room.AcceptType.ACCEPTED) {
                        connections.put(client, room);
                    }
                    return;
                }
            }

            String roomName = joinRequest.roomName;
            if (useRooms && !roomName.isEmpty()) {
                Room room = new Room(roomName, joinRequest.roomPass);
                rooms.add(room);
                Room.AcceptType acceptType = room.checkAccept(client, joinRequest);
                if (acceptType != Room.AcceptType.ACCEPTED) {
                    rooms.remove(room);
                } else {
                    connections.put(client, room);
                }
                return;
            }

            rejectConnection(client, useRooms ? "This server uses rooms! Please input a room name and (optionally) a password to connect to or create a room." : "Failed to connect: unknown reason (this should not happen)");

        } catch (Exception e) {
            System.out.println("Failed to accept " + client + " due to exception: " + e);
            rejectConnection(client, "There was an error trying to the connection.");
            e.printStackTrace();
        }
    }

    private static synchronized void clearEmptyOrClosedRooms() {
        if (useRooms)
            rooms.stream().filter(room -> room.isClosed() || room.isEmpty()).peek(Room::close).collect(Collectors.toList()).forEach(rooms::remove);
    }

    public static void rejectConnection(WebSocket client, String msg) {
        try {
            client.send(new JoinRequestResponse(false, msg).toJson());
        } catch (Exception ignored) {
        }
        client.close();
    }

    public static String[] withoutFirstArg(String[] args) {
        return Arrays.copyOfRange(args, Math.min(1, args.length), args.length);
    }

    private static class NinjaLinkWSS extends WebSocketServer {
        public NinjaLinkWSS(int port) {
            super(new InetSocketAddress(port));
        }

        @Override
        public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        }

        @Override
        public void onClose(WebSocket webSocket, int i, String s, boolean b) {
            Optional.ofNullable(connections.remove(webSocket)).ifPresent(room -> room.removeUser(webSocket));
        }

        @Override
        public void onMessage(WebSocket webSocket, String s) {
            if (!connections.containsKey(webSocket)) {
                acceptNewClient(webSocket, s);
                return;
            }
            try {
                connections.get(webSocket).onReceiveString(webSocket, s);
            } catch (IOException e) {
                onError(webSocket, e);
                webSocket.close();
            }
        }

        @Override
        public void onError(WebSocket webSocket, Exception e) {
            System.out.println("Error in websocket: " + e);
            e.printStackTrace();
        }

        @Override
        public void onStart() {
            System.out.println("Websocket started on port " + this.getPort());
        }
    }
}
