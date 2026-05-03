# Compile (all files together — LoginWindow and RoundBorder unchanged)
javac Server.java ClientHandler.java ChatClient.java ChatWindow.java LoginWindow.java RoundBorder.java

# Terminal 1 — start server
java Server

# Terminal 2, 3, 4 — open multiple clients
java LoginWindow