import java.io.*;
import java.net.*;

/**
 * ChatClient.java  [UPDATED]
 * ──────────────────────────
 * Changes from original:
 *   • MessageListener now has two extra callbacks:
 *       onOnlineCountReceived(int count)
 *       onUserListReceived(String[] usernames)
 *
 *   • readLoop() checks the message prefix and routes each line
 *     to the right callback instead of sending everything to onMessageReceived().
 *
 *     CHAT:…          → onMessageReceived  (chat text, strip prefix before passing)
 *     ONLINE_COUNT:…  → onOnlineCountReceived
 *     USER_LIST:…     → onUserListReceived
 *
 * Everything else (connect, sendMessage, disconnect) is unchanged.
 */
public class ChatClient {

    private static final String SERVER_IP   = "localhost";
    private static final int    SERVER_PORT = 12345;

    private Socket         socket;
    private BufferedReader in;
    private PrintWriter    out;

    private final MessageListener listener;
    private volatile boolean running = false;

    // ── Listener interface ────────────────────────────────────

    /**
     * UPDATED: three new callbacks added for the sidebar feature.
     * ChatWindow implements all five methods.
     */
    public interface MessageListener {
        /** A normal chat message (timestamp + username + text). */
        void onMessageReceived(String message);

        /** Server sent a fresh user count. Update the "N online" label. */
        void onOnlineCountReceived(int count);

        /** Server sent the full user list. Rebuild the sidebar JList. */
        void onUserListReceived(String[] usernames);

        /** Connection dropped unexpectedly. */
        void onDisconnected(String reason);
    }

    // ── Constructor ──────────────────────────────────────────
    public ChatClient(MessageListener listener) {
        this.listener = listener;
    }

    // ── Connect ───────────────────────────────────────────────
    public void connect(String username) throws IOException {
        socket = new Socket(SERVER_IP, SERVER_PORT);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        running = true;

        // Discard the server's "Enter your username: " prompt, then send ours
        in.readLine();
        out.println(username);

        // Daemon background thread — reads and routes every line from the server
        Thread readerThread = new Thread(this::readLoop, "chat-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    // ── Read loop (background thread) ─────────────────────────

    /**
     * UPDATED: instead of passing every line straight to onMessageReceived(),
     * we now inspect the prefix first and call the right callback.
     *
     * Protocol prefixes:
     *   CHAT:          → regular chat line (strip prefix, pass to onMessageReceived)
     *   ONLINE_COUNT:  → parse integer, call onOnlineCountReceived
     *   USER_LIST:     → split by comma, call onUserListReceived
     */
    private void readLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {

                if (line.startsWith("CHAT:")) {
                    // Strip the prefix and send the clean text to the UI
                    final String chatText = line.substring(5); // "CHAT:".length() == 5
                    listener.onMessageReceived(chatText);

                } else if (line.startsWith("ONLINE_COUNT:")) {
                    try {
                        int count = Integer.parseInt(line.substring(13).trim());
                        listener.onOnlineCountReceived(count);
                    } catch (NumberFormatException ignored) {}

                } else if (line.startsWith("USER_LIST:")) {
                    String raw = line.substring(10).trim(); // "USER_LIST:".length() == 10
                    // Split on comma; if the list is empty, produce a zero-length array
                    String[] users = raw.isEmpty() ? new String[0] : raw.split(",");
                    listener.onUserListReceived(users);

                } else {
                    // Fallback: unknown line — show it as a plain chat message
                    listener.onMessageReceived(line);
                }
            }
        } catch (IOException e) {
            if (running) {
                listener.onDisconnected("Connection lost: " + e.getMessage());
            }
        }
    }

    // ── Send / Disconnect ─────────────────────────────────────

    public void sendMessage(String text) {
        if (out != null) out.println(text);
    }

    public void disconnect() {
        running = false;
        sendMessage("/exit");
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("Error closing socket: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }
}
