import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;

/**
 * ClientHandler.java  [UPDATED]
 * ─────────────────────────────
 * Changes from original:
 *   • All chat messages are now sent with the "CHAT:" prefix
 *     so the client can distinguish them from control messages.
 *   • After a user joins or leaves, Server.broadcastUserState()
 *     is called to push fresh ONLINE_COUNT and USER_LIST to everyone.
 *   • getUsername() getter added so Server can build the user list.
 *
 * Everything else (run loop, /exit, disconnect) is unchanged.
 */
public class ClientHandler implements Runnable {

    private Socket         socket;
    private BufferedReader in;
    private PrintWriter    out;
    private String         username;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Ask for username
            out.println("Enter your username: ");
            username = in.readLine();

            if (username == null || username.trim().isEmpty()) {
                username = "Unknown";
            }
            username = username.trim();

            // ── Join announcement ─────────────────────────────
            // CHANGED: prefix with CHAT: so the client renders it as a chat message
            String joinMsg = "CHAT:" + getTimestamp() + " *** " + username + " has joined the chat! ***";
            System.out.println("[Server] " + username + " joined.");
            Server.broadcast(joinMsg);

            // Send welcome info directly to the new client only (no prefix needed —
            // these are informational lines shown before the chat starts)
            out.println("CHAT:-------------------------------------------");
            out.println("CHAT: Welcome, " + username + "! You are now connected.");
            out.println("CHAT: Type your message and press Enter to send.");
            out.println("CHAT: Type /exit to leave the chat.");
            out.println("CHAT:-------------------------------------------");

            // ── NEW: push fresh online count + user list to everyone ──
            Server.broadcastUserState();

            // ── Main message loop ─────────────────────────────
            String message;
            while ((message = in.readLine()) != null) {

                if (message.equalsIgnoreCase("/exit")) {
                    break;
                }

                if (message.trim().isEmpty()) {
                    continue;
                }

                // CHANGED: prefix normal messages with CHAT:
                String formatted = "CHAT:" + getTimestamp() + " " + username + ": " + message;
                System.out.println(formatted);
                Server.broadcast(formatted);
            }

        } catch (IOException e) {
            System.out.println("[Server] Connection lost: " + username);
        } finally {
            disconnect();
        }
    }

    /** Sends a message string directly to this client's output stream. */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /** NEW: lets Server.broadcastUserState() read the username. */
    public String getUsername() {
        return username != null ? username : "";
    }

    private void disconnect() {
        Server.removeClient(this);

        // ── Leave announcement ────────────────────────────────
        String leaveMsg = "CHAT:" + getTimestamp() + " *** " + username + " has left the chat. ***";
        System.out.println("[Server] " + username + " left.");
        Server.broadcast(leaveMsg);

        // ── NEW: push updated count + list after removal ──────
        Server.broadcastUserState();

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("[Server] Error closing socket for: " + username);
        }
    }

    private String getTimestamp() {
        return "[" + LocalTime.now().format(TIME_FORMAT) + "]";
    }
}
