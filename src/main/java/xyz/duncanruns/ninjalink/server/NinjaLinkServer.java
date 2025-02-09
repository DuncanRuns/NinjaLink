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
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public final class NinjaLinkServer {

    private static final Set<Room> rooms = new HashSet<>();
    private static boolean useRooms;
    private static ServerSocket serverSocket;

    private NinjaLinkServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = 52534;
        if (args.length > 0) port = Integer.parseInt(args[0].equalsIgnoreCase("default") ? "52534" : args[0]);
        useRooms = Arrays.stream(args).skip(1).anyMatch(s -> s.toLowerCase().contains("rooms"));
        if (!useRooms) rooms.add(new Room());

        if (!SocketUtil.isPortFree(port)) {
            System.out.printf("Port %d is already in use!\n", port);
        }

        serverSocket = new ServerSocket(port);

        if (!serverSocket.isBound()) {
            System.out.println("Server socket did not bind!");
            return;
        }

        Thread inputThread = new Thread(NinjaLinkServer::inputLoop);
        inputThread.setDaemon(true);
        inputThread.start();

        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                acceptNewClient(client);
            } catch (Exception e) {
                if (serverSocket.isClosed()) return;
                throw e;
            }
        }
    }

    private static void inputLoop() {
        while (!serverSocket.isClosed()) {
            Scanner scanner = new Scanner(System.in);
            String line = scanner.nextLine();

            String[] args = line.split("[ \\t]+");

            runCommand(args);
        }
    }

    public static synchronized void runCommand(String[] args) {
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
                break;
            default:
                System.out.println("Invalid command!");
                break;
        }
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
        SocketUtil.carelesslyClose(serverSocket);
        rooms.forEach(Room::close);
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

            if (!joinRequest.isValid()) {
                rejectConnection(client, "Invalid join request!");
                return;
            }

            if (!joinRequest.isWatcher() && joinRequest.nickname.trim().isEmpty()) {
                rejectConnection(client, "Name cannot be empty!");
                return;
            }

            clearEmptyOrClosedRooms();

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

    private static synchronized void clearEmptyOrClosedRooms() {
        if (useRooms)
            rooms.stream().filter(room -> room.isClosed() || room.isEmpty()).peek(Room::close).collect(Collectors.toList()).forEach(rooms::remove);
    }

    public static void rejectConnection(Socket client, String msg) {
        try {
            SocketUtil.sendStringWithLength(client, new JoinRequestResponse(false, msg).toJson());
        } catch (Exception ignored) {
        }
        SocketUtil.carelesslyClose(client);
    }

    public static String[] withoutFirstArg(String[] args) {
        return Arrays.copyOfRange(args, Math.min(1, args.length), args.length);
    }
}
