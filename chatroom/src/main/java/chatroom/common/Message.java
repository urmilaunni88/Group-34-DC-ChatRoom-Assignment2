package chatroom.common;

import java.io.Serializable;

/**
 * Protocol messages exchanged between user nodes for Lamport's DME algorithm
 * and between user nodes and the file server.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        // --- Lamport DME messages ---
        REQUEST,   // "I want the critical section"
        REPLY,     // "You can go ahead"
        RELEASE,   // "I am done with the critical section"

        // --- File-server messages ---
        VIEW,      // Request to read the shared file
        POST,      // Request to append text to the shared file
        RESPONSE   // Generic response from server
    }

    private final Type   type;
    private final int    senderId;
    private final long   timestamp;   // Lamport clock value
    private final String payload;     // text for POST / content for RESPONSE

    public Message(Type type, int senderId, long timestamp, String payload) {
        this.type      = type;
        this.senderId  = senderId;
        this.timestamp = timestamp;
        this.payload   = payload;
    }

    public Type   getType()      { return type; }
    public int    getSenderId()  { return senderId; }
    public long   getTimestamp() { return timestamp; }
    public String getPayload()   { return payload; }

    @Override
    public String toString() {
        return String.format("[%s | sender=%d | ts=%d | payload=%s]",
                type, senderId, timestamp, payload);
    }
}
