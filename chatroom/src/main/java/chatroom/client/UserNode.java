package chatroom.client;

import chatroom.common.Config;
import chatroom.common.Message;
import chatroom.common.Message.Type;
import chatroom.dme.LamportDME;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.*;

/**
 * ============================================================
 * User Node (Node 1 or Node 2)
 * ============================================================
 *
 * Usage:
 *   java -cp . chatroom.client.UserNode <nodeId> <username>
 *
 *   nodeId  : 1 or 2
 *   username: display name shown in posts
 *
 * CLI commands:
 *   view           – fetch and display all messages
 *   post <text>    – acquire mutex, post message, release mutex
 *   quit           – exit
 */
public class UserNode {

    private static final Logger LOG = Logger.getLogger(UserNode.class.getName());
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd MMM h:mma");

    private final int        myId;
    private final String     username;
    private final LamportDME dme;

    public UserNode(int myId, String username, List<Integer> peers) {
        this.myId     = myId;
        this.username = username;
        this.dme      = new LamportDME(myId, peers);
    }

    // ------------------------------------------------------------------
    // File server communication
    // ------------------------------------------------------------------

    /** VIEW: Pull entire chat file from server and print it. */
    public void view() {
        Message req = new Message(Type.VIEW, myId, 0, null);
        Message res = sendToServer(req);
        if (res != null) {
            System.out.println("\n--- Chat Room Messages ---");
            System.out.println(res.getPayload());
            System.out.println("--------------------------\n");
        } else {
            System.err.println("ERROR: Could not reach file server.");
        }
    }

    /**
     * POST: Acquire DME CS → append text to server file → release CS.
     * Also logs the DME activity so it can be demonstrated.
     */
    public void post(String text) throws InterruptedException {
        String timestamp = LocalDateTime.now().format(FMT);
        String entry     = timestamp + " " + username + ": " + text;

        System.out.println("[DME] Requesting critical section...");
        dme.acquireCS();   // ← Lamport mutual exclusion
        System.out.println("[DME] Critical section ACQUIRED. Posting...");

        try {
            // gives you time to type on the other node
           // System.out.println("[DME] Holding CS for 15 seconds (demo delay)...");
            //gvThread.sleep(15000);
            Message req = new Message(Type.POST, myId, 0, entry);
            Message res = sendToServer(req);
            if (res != null && "OK".equals(res.getPayload())) {
                System.out.println("Posted: " + entry);
            } else {
                System.err.println("ERROR: Post failed.");
            }
        } finally {
            dme.releaseCS();  // always release even if post failed
            System.out.println("[DME] Critical section RELEASED.");
        }
    }

    private Message sendToServer(Message req) {
        try (Socket s   = new Socket(Config.SERVER_HOST, Config.SERVER_FILE_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream  in  = new ObjectInputStream(s.getInputStream())) {

            out.writeObject(req);
            out.flush();
            return (Message) in.readObject();

        } catch (Exception e) {
            LOG.warning("Server communication error: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // REPL
    // ------------------------------------------------------------------

    public void runCLI() throws Exception {
        dme.start();
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Chat Room ===");
        System.out.println("Node: " + myId + "  User: " + username);
        System.out.println("Commands: view | post <text> | quit");
        System.out.println("=================\n");

        while (true) {
            System.out.print(username + "_node> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye!");
                break;

            } else if (line.equalsIgnoreCase("view")) {
                view();

            } else if (line.toLowerCase().startsWith("post ")) {
                String text = line.substring(5).trim();
                if (text.isEmpty()) {
                    System.out.println("Usage: post <text>");
                } else {
                    post(text);
                }

            } else {
                System.out.println("Unknown command. Use: view | post <text> | quit");
            }
        }

        dme.stop();
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java chatroom.client.UserNode <nodeId> <username>");
            System.err.println("  nodeId: 1 or 2");
            System.exit(1);
        }

        int    nodeId   = Integer.parseInt(args[0]);
        String username = args[1];

        if (nodeId != 1 && nodeId != 2) {
            System.err.println("nodeId must be 1 or 2");
            System.exit(1);
        }

        // Peer list: all user nodes except self
        List<Integer> peers = new ArrayList<>();
        for (int id : new int[]{1, 2}) {
            if (id != nodeId) peers.add(id);
        }

        UserNode node = new UserNode(nodeId, username, peers);
        node.runCLI();
    }
}
