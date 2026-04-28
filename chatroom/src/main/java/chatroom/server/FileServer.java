package chatroom.server;

import chatroom.common.Config;
import chatroom.common.Message;
import chatroom.common.Message.Type;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.logging.*;

/**
 * ============================================================
 * File Server (Node 0)
 * ============================================================
 * Responsibilities:
 *  - Store the shared chat file (chatroom.txt).
 *  - Serve VIEW requests  → return entire file contents.
 *  - Serve POST requests  → append text (with timestamp + user) to file.
 *
 * Access control (mutual exclusion) is entirely handled by the
 * user nodes via Lamport's DME BEFORE they contact this server,
 * so the server itself is stateless w.r.t. locking.
 */
public class FileServer {

    private static final Logger LOG = Logger.getLogger(FileServer.class.getName());

    private final int        port;
    private final Path       sharedFile;
    private ServerSocket     serverSocket;

    public FileServer(int port, String filePath) {
        this.port       = port;
        this.sharedFile = Paths.get(filePath);
    }

    public void start() throws IOException {
        // Create shared file if it doesn't exist
        if (!Files.exists(sharedFile)) {
            Files.createFile(sharedFile);
            LOG.info("Created shared file: " + sharedFile.toAbsolutePath());
        }

        serverSocket = new ServerSocket(port);
        LOG.info("File server listening on port " + port);

        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client), "fs-handler").start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) LOG.warning("Accept error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        try (ObjectInputStream  in  = new ObjectInputStream(client.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream())) {

            Message req = (Message) in.readObject();
            LOG.info("Received " + req.getType() + " from node " + req.getSenderId());

            Message response;
            if (req.getType() == Type.VIEW) {
                String content = Files.readString(sharedFile);
                response = new Message(Type.RESPONSE, Config.SERVER_ID, 0,
                                       content.isEmpty() ? "(no messages yet)" : content);
            } else if (req.getType() == Type.POST) {
                Files.writeString(sharedFile, req.getPayload() + System.lineSeparator(),
                                  StandardOpenOption.APPEND);
                response = new Message(Type.RESPONSE, Config.SERVER_ID, 0, "OK");
                LOG.info("Appended to file: " + req.getPayload());
            } else {
                response = new Message(Type.RESPONSE, Config.SERVER_ID, 0, "ERROR: unknown command");
            }

            out.writeObject(response);
            out.flush();

        } catch (Exception e) {
            LOG.warning("Error handling client: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        LOG.info("=== Starting File Server (Node 0) ===");
        FileServer srv = new FileServer(Config.SERVER_FILE_PORT, Config.SHARED_FILE);
        srv.start();
    }
}
