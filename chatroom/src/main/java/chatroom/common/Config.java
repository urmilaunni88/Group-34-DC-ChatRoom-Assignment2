package chatroom.common;

public class Config {
    // Node IDs
    public static final int SERVER_ID  = 0;
    public static final int NODE1_ID   = 1;
    public static final int NODE2_ID   = 2;

    // Hosts (change these to your actual IPs/hostnames)
    public static final String SERVER_HOST = "localhost";
    public static final String NODE1_HOST  = "localhost";
    public static final String NODE2_HOST  = "localhost";

    // Ports – server exposes a file-service port; each node exposes a DME port
    public static final int SERVER_FILE_PORT = 5000;
    public static final int NODE1_DME_PORT   = 5001;
    public static final int NODE2_DME_PORT   = 5002;

    // Shared file name (kept on the server node)
    public static final String SHARED_FILE = "chatroom.txt";

    /** Returns the DME port for a given node id (1 or 2). */
    public static int dmePort(int nodeId) {
        return nodeId == 1 ? NODE1_DME_PORT : NODE2_DME_PORT;
    }

    /** Returns the hostname for a given node id (1 or 2). */
    public static String dmeHost(int nodeId) {
        return nodeId == 1 ? NODE1_HOST : NODE2_HOST;
    }
}
