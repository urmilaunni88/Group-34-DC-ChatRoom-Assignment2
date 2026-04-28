package chatroom.dme;

import chatroom.common.Config;
import chatroom.common.Message;
import chatroom.common.Message.Type;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * ============================================================
 * Lamport's Distributed Mutual Exclusion Algorithm
 * ============================================================
 *
 * Algorithm summary (for N=2 user nodes):
 *  1. To REQUEST CS:
 *       - Increment clock; record own REQUEST(ts, id) in queue.
 *       - Broadcast REQUEST to all other nodes.
 *       - Wait until:
 *           (a) own request is at the head of the priority queue, AND
 *           (b) a REPLY has been received from every other node.
 *
 *  2. On receiving REQUEST(ts, j):
 *       - Update clock; add request to priority queue.
 *       - Send REPLY to j.
 *
 *  3. On receiving REPLY from j:
 *       - Update clock; record that j has replied.
 *
 *  4. To RELEASE CS:
 *       - Remove own request from priority queue.
 *       - Broadcast RELEASE to all other nodes.
 *
 *  5. On receiving RELEASE from j:
 *       - Remove j's request from the priority queue.
 *
 * Priority queue ordering: (timestamp, node-id) — ties broken by node-id.
 */
public class LamportDME {

    private static final Logger LOG = Logger.getLogger(LamportDME.class.getName());

    // Identity of THIS node
    private final int myId;
    // IDs of ALL other user nodes (peer list)
    private final List<Integer> peers;

    private final LamportClock clock = new LamportClock();

    // Priority queue of pending CS requests — sorted by (ts, id)
    private final PriorityQueue<long[]> requestQueue = new PriorityQueue<>(
        Comparator.comparingLong((long[] e) -> e[0]).thenComparingLong(e -> e[1])
    );

    // Set of node IDs that have sent us a REPLY for the current request
    private final Set<Integer> replies = ConcurrentHashMap.newKeySet();

    // Whether we currently want the CS
    private volatile boolean wantCS = false;

    // Listener thread + port
    private final int listenPort;
    private Thread listenerThread;
    private ServerSocket serverSocket;

    /**
     * @param myId      This node's ID (1 or 2)
     * @param peers     IDs of the other user nodes
     */
    public LamportDME(int myId, List<Integer> peers) {
        this.myId       = myId;
        this.peers      = Collections.unmodifiableList(peers);
        this.listenPort = Config.dmePort(myId);
    }

    // ------------------------------------------------------------------
    // Start / Stop
    // ------------------------------------------------------------------

    /** Start the background listener that handles incoming DME messages. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(listenPort);
        listenerThread = new Thread(this::listenLoop, "dme-listener-" + myId);
        listenerThread.setDaemon(true);
        listenerThread.start();
        LOG.info("Node " + myId + " DME listener started on port " + listenPort);
    }

    public void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    // ------------------------------------------------------------------
    // Public API – called by the application layer
    // ------------------------------------------------------------------

    /**
     * Acquire the critical section (blocks until granted).
     * Call this BEFORE any write (post) operation.
     */
    public synchronized void acquireCS() throws InterruptedException {
        wantCS = true;
        replies.clear();

        long ts = clock.tick();
        synchronized (requestQueue) {
            requestQueue.add(new long[]{ts, myId});
        }

        LOG.info("[DME][Node " + myId + "] REQUESTING CS at ts=" + ts);
        broadcast(new Message(Type.REQUEST, myId, ts, null));

        // Wait until conditions satisfied
        while (!canEnterCS()) {
            wait(50); // wake periodically to recheck
        }
        LOG.info("[DME][Node " + myId + "] ENTERING CS");
    }

    /**
     * Release the critical section.
     * Call this AFTER the write (post) operation completes.
     */
    public synchronized void releaseCS() {
        long ts = clock.tick();
        synchronized (requestQueue) {
            requestQueue.removeIf(e -> e[1] == myId);
        }
        wantCS = false;
        LOG.info("[DME][Node " + myId + "] RELEASING CS at ts=" + ts);
        broadcast(new Message(Type.RELEASE, myId, ts, null));
        notifyAll();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private boolean canEnterCS() {
        if (!wantCS) return false;
        if (replies.size() < peers.size()) return false;
        synchronized (requestQueue) {
            long[] head = requestQueue.peek();
            return head != null && head[1] == myId;
        }
    }

    private void broadcast(Message msg) {
        for (int peerId : peers) {
            sendTo(peerId, msg);
        }
    }

    private void sendTo(int targetId, Message msg) {
        String host = Config.dmeHost(targetId);
        int    port = Config.dmePort(targetId);
        // Retry a few times in case the peer hasn't started yet
        for (int attempt = 0; attempt < 5; attempt++) {
            try (Socket s = new Socket(host, port);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
                return;
            } catch (IOException e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }
        LOG.warning("[DME] Could not reach node " + targetId);
    }

    private void listenLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                new Thread(() -> handleIncoming(client), "dme-handler").start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) LOG.warning("Accept error: " + e.getMessage());
            }
        }
    }

    private void handleIncoming(Socket client) {
        try (ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            Message msg = (Message) in.readObject();
            processMessage(msg);
        } catch (Exception e) {
            LOG.warning("Error reading DME message: " + e.getMessage());
        }
    }

    private synchronized void processMessage(Message msg) {
        clock.update(msg.getTimestamp());
        int    sender = msg.getSenderId();
        long   ts     = msg.getTimestamp();

        switch (msg.getType()) {
            case REQUEST:
                LOG.info("[DME][Node " + myId + "] Received REQUEST from " + sender + " ts=" + ts);
                synchronized (requestQueue) {
                    requestQueue.add(new long[]{ts, sender});
                }
                // Immediately reply
                long replyTs = clock.tick();
                sendTo(sender, new Message(Type.REPLY, myId, replyTs, null));
                break;

            case REPLY:
                LOG.info("[DME][Node " + myId + "] Received REPLY from " + sender);
                replies.add(sender);
                notifyAll();
                break;

            case RELEASE:
                LOG.info("[DME][Node " + myId + "] Received RELEASE from " + sender);
                synchronized (requestQueue) {
                    requestQueue.removeIf(e -> e[1] == sender);
                }
                notifyAll();
                break;

            default:
                LOG.warning("[DME] Unknown message type: " + msg.getType());
        }
    }
}
