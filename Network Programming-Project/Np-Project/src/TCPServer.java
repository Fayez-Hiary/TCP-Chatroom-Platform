import java.net.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

public class TCPServer {
    static List<ChatRoom> chatRooms = Collections.synchronizedList(new ArrayList<>());
    static List<String> connectedNicknames = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(2000)) {
            System.out.println("Server started");
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler extends Thread {
    private Socket socket;
    private BufferedReader br;
    private PrintWriter pw;
    private String nickname;
    private volatile boolean roomClosedByOwner = false; // Added volatile for thread visibility

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void sendMessage(String msg) {
        pw.println(msg);
    }

    public void notifyRoomClosed() {
        this.roomClosedByOwner = true;
    }

    @Override
    public void run() {
        try {
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Nickname validation loop - check for duplicates
            while (true) {
                nickname = br.readLine();
                if (nickname == null)
                    return;

                synchronized (TCPServer.connectedNicknames) {
                    if (TCPServer.connectedNicknames.contains(nickname)) {
                        pw.println("NICKNAME_TAKEN");
                    } else {
                        TCPServer.connectedNicknames.add(nickname);
                        pw.println("NICKNAME_OK");
                        break;
                    }
                }
            }
            System.out.println("User Connected: " + nickname);

            while (true) {
                String line = br.readLine();
                if (line == null)
                    break;

                int choice;
                try {
                    choice = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    continue;
                }

                switch (choice) {
                    case 1:
                        createChatRoom();
                        break;
                    case 2:
                        joinChatRoom();
                        break;
                    case 3:
                        System.out.println("User " + nickname + " Disconnected");
                        TCPServer.connectedNicknames.remove(nickname);
                        socket.close();
                        return;
                }
            }
        } catch (Exception e) {
            System.out.println(nickname + " disconnected unexpectedly.");
            if (nickname != null) {
                TCPServer.connectedNicknames.remove(nickname);
            }
        }
    }

    private void createChatRoom() throws IOException {
        String passcode = br.readLine();
        ChatRoom room = new ChatRoom(nickname, passcode);
        synchronized (TCPServer.chatRooms) {
            TCPServer.chatRooms.add(room);
        }
        pw.println("Successful Chatroom Created.");
        handleChat(room);
    }

    private void joinChatRoom() throws IOException {
        synchronized (TCPServer.chatRooms) {
            if (TCPServer.chatRooms.isEmpty()) {
                pw.println("NO_ROOMS");
                return;
            }
            pw.println(TCPServer.chatRooms.size());
            for (ChatRoom r : TCPServer.chatRooms) {
                pw.println(r.getOwner());
                pw.println(r.getCreationTime());
            }
        }

        String indexLine = br.readLine();
        if (indexLine == null)
            return;
        int index = Integer.parseInt(indexLine);

        ChatRoom room;
        synchronized (TCPServer.chatRooms) {
            if (index < 0 || index >= TCPServer.chatRooms.size()) {
                pw.println("INVALID_ROOM");
                return;
            }
            room = TCPServer.chatRooms.get(index);
        }

        pw.println("OK_INDEX");
        String pass = br.readLine();
        if (!room.getPasscode().equals(pass)) {
            pw.println("WRONG_PASS");
            return;
        }

        pw.println("OK");
        handleChat(room);
    }

    private void handleChat(ChatRoom room) throws IOException {
        room.addClient(this);
        pw.println("CHAT_START");
        room.broadcast("<" + nickname + "> has joined!", this);

        while (true) {
            // If the owner closed the room, exit loop immediately
            if (roomClosedByOwner)
                break;

            if (br.ready()) { // Check if there is data to read
                String msg = br.readLine();
                if (msg == null)
                    break;

                if (msg.equalsIgnoreCase("exit")) {
                    if (nickname.equals(room.getOwner())) {
                        room.broadcast("Chatroom closed by owner.", this);
                        room.closeRoomExcept(this);
                        synchronized (TCPServer.chatRooms) {
                            TCPServer.chatRooms.remove(room);
                        }
                    } else {
                        room.broadcast("<" + nickname + "> has left.", this);
                        room.removeClient(this);
                    }
                    pw.println("EXIT_CONFIRMED");
                    break;
                }
                room.broadcast(nickname + ": " + msg, this);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        roomClosedByOwner = false; // Reset for next session
    }
}

class ChatRoom {
    private String owner;
    private String passcode;
    private LocalDateTime creationTime;
    private List<ClientHandler> clients = new ArrayList<>();

    public ChatRoom(String owner, String passcode) {
        this.owner = owner;
        this.passcode = passcode;
        this.creationTime = LocalDateTime.now();
    }

    public synchronized void addClient(ClientHandler ch) {
        clients.add(ch);
    }

    public synchronized void removeClient(ClientHandler ch) {
        clients.remove(ch);
    }

    public synchronized void closeRoomExcept(ClientHandler owner) {
        for (ClientHandler c : clients) {
            if (c != owner) {
                c.sendMessage("ROOM_CLOSED");
                c.notifyRoomClosed();
            }
        }
        clients.clear();
    }

    public synchronized void broadcast(String msg, ClientHandler sender) {
        for (ClientHandler c : clients) {
            if (c != sender)
                c.sendMessage(msg);
        }
    }

    public String getOwner() {
        return owner;
    }

    public String getPasscode() {
        return passcode;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }
}