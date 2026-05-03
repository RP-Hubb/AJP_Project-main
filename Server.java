import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server.java  [UPDATED]
 * ─────────────────────
 * Changes from original:
 *   • broadcast() now uses a message prefix protocol:
 *       CHAT:<text>         → a normal chat message
 *       ONLINE_COUNT:<n>    → how many users are online right now
 *       USER_LIST:<a,b,c>   → comma-separated list of usernames
 *
 *   • broadcastUserState() is a new helper called after every
 *     join and leave — it pushes the fresh count and user list
 *     to every connected client so their sidebar stays in sync.
 *
 * Everything else (ServerSocket loop, threading) is unchanged.
 */
public class Server {

    private static final int PORT = 12345;

    // Shared list of active ClientHandler threads
    // synchronizedList makes it safe for concurrent thread access
    static List<ClientHandler> clients =
            Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("   Real-Time Group Chat Server Started    ");
        System.out.println("   Listening on port: " + PORT);
        System.out.println("===========================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                new Thread(handler).start();
                System.out.println("[Server] New connection: " + clientSocket.getInetAddress());
            }
        } catch (IOException e) {
            System.out.println("[Server] Error: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a prefixed message to every connected client.
     *
     * All messages now carry a prefix so the client can tell them apart:
     *   CHAT:Hello world
     *   ONLINE_COUNT:3
     *   USER_LIST:Alice,Bob,Charlie
     */
    static void broadcast(String prefixedMessage) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendMessage(prefixedMessage);
            }
        }
    }

    /**
     * NEW — called after any join or leave event.
     * Sends two control messages to every client:
     *   1. ONLINE_COUNT:<number of connected users>
     *   2. USER_LIST:<comma-separated usernames>
     *
     * The client-side code watches for these prefixes and updates
     * the sidebar and online-count label accordingly.
     */
    static void broadcastUserState() {
        synchronized (clients) {
            int count = clients.size();

            // Build comma-separated username list, e.g. "Alice,Bob,Charlie"
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < clients.size(); i++) {
                if (i > 0) names.append(",");
                names.append(clients.get(i).getUsername());
            }

            // Send both control messages to everyone
            String countMsg = "ONLINE_COUNT:" + count;
            String listMsg  = "USER_LIST:"    + names;

            System.out.println("[Server] " + countMsg + " | " + listMsg);

            for (ClientHandler c : clients) {
                c.sendMessage(countMsg);
                c.sendMessage(listMsg);
            }
        }
    }

    /** Removes a disconnected client from the list. */
    static void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }
}
