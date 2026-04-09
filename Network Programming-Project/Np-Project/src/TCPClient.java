import java.net.*;
import java.io.*;
import java.util.Scanner;

public class TCPClient {
    public static void main(String[] args) {
        try (Scanner input = new Scanner(System.in);
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), 2000);
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            // Nickname validation loop - keep asking until unique
            while (true) {
                System.out.print("Enter your nickname: ");
                pw.println(input.nextLine());
                String response = br.readLine();
                if (response.equals("NICKNAME_OK")) {
                    break;
                } else if (response.equals("NICKNAME_TAKEN")) {
                    System.out.println("This nickname is already taken. Please choose another.");
                }
            }

            while (true) {
                System.out.println("\n--- Main Menu ---");
                System.out.println("1. Start a new chatroom");
                System.out.println("2. Join an open chatroom");
                System.out.println("3. Log off");
                System.out.print("Choice: ");

                String choiceStr = input.nextLine();
                pw.println(choiceStr);

                if (choiceStr.equals("1")) {
                    System.out.print("Enter chatroom passcode: ");
                    pw.println(input.nextLine());
                    System.out.println(br.readLine());
                    startChat(br, pw, input);
                } else if (choiceStr.equals("2")) {
                    String first = br.readLine();
                    if (first.equals("NO_ROOMS")) {
                        System.out.println("No chatrooms available.");
                        continue;
                    }
                    int count = Integer.parseInt(first);
                    for (int i = 0; i < count; i++) {
                        System.out.println((i + 1) + ". Owner: " + br.readLine() + " | Created: " + br.readLine());
                    }
                    System.out.print("Select chatroom number: ");
                    pw.println(Integer.parseInt(input.nextLine()) - 1);

                    String res = br.readLine();
                    if (res.equals("INVALID_ROOM")) {
                        System.out.println("Invalid choice.");
                        continue;
                    }
                    System.out.print("Enter passcode: ");
                    pw.println(input.nextLine());

                    if (br.readLine().equals("OK")) {
                        System.out.println("Joined successfully.");
                        startChat(br, pw, input);
                    } else {
                        System.out.println("Incorrect passcode.");
                    }
                } else if (choiceStr.equals("3")) {
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Connection lost.");
        }
    }

    private static void startChat(BufferedReader br, PrintWriter pw, Scanner input) {
        try {
            if (!br.readLine().equals("CHAT_START"))
                return;

            System.out.println("Type messages ('exit' to leave):");
            final boolean[] active = { true };

            Thread receiver = new Thread(() -> {
                try {
                    String msg;
                    while (active[0] && (msg = br.readLine()) != null) {
                        if (msg.equals("ROOM_CLOSED")) {
                            System.out.println("\n[System] Room closed by owner. Press Enter to return to menu.");
                            active[0] = false;
                            break;
                        }
                        if (msg.equals("EXIT_CONFIRMED")) {
                            active[0] = false;
                            break;
                        }
                        System.out.println(msg);
                    }
                } catch (IOException e) {
                    active[0] = false;
                }
            });
            receiver.start();

            while (active[0]) {
                if (System.in.available() > 0) {
                    String msg = input.nextLine();
                    if (active[0]) {
                        pw.println(msg);
                        if (msg.equalsIgnoreCase("exit"))
                            break;
                    }
                }
                Thread.sleep(100);
            }
            receiver.join(); // Wait for receiver to finish reading server signals
        } catch (Exception e) {
            System.out.println("Error in chat session.");
        }
    }
}