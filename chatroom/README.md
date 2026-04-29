# Distributed Chat Room — DC Assignment II
### Implementation: Lamport's Distributed Mutual Exclusion (Java)

---

## Project Structure

```
chatroom/
├── build.sh
├── chatroom.jar                  (after build)
└── src/main/java/chatroom/
    ├── common/
    │   ├── Config.java           ← IPs, ports, filenames
    │   └── Message.java          ← Serializable protocol message
    ├── dme/
    │   ├── LamportClock.java     ← Thread-safe logical clock
    │   └── LamportDME.java       ← Lamport's DME algorithm (the middleware)
    ├── server/
    │   └── FileServer.java       ← Node 0: file storage service
    └── client/
        └── UserNode.java         ← Node 1 & 2: user-facing CLI + app logic
```

---

## Prerequisites

| Tool    | Version |
|---------|---------|
| Java    | 11+     |
| bash    | any     |

---

## Step 1 – Configure IP Addresses

Edit `src/main/java/chatroom/common/Config.java` and set the real IP addresses of your 3 cloud nodes:

```java
public static final String SERVER_HOST = "10.0.0.1";  // Machine 0
public static final String NODE1_HOST  = "10.0.0.2";  // Machine 1
public static final String NODE2_HOST  = "10.0.0.3";  // Machine 2
```

Ports (defaults, change if needed):

| Role                | Port |
|---------------------|------|
| File server (R/W)   | 5000 |
| Node 1 DME listener | 5001 |
| Node 2 DME listener | 5002 |

---

## Step 2 – Build

On any machine (needs JDK):

```bash
chmod +x build.sh
./build.sh
```

This produces `chatroom.jar`. Copy it to all 3 machines.

---

## Step 3 – Run (in order)

### On Machine 0 (Server Node):
```bash
java -cp chatroom.jar chatroom.server.FileServer
```

### On Machine 1 (User Node 1):
```bash
java -cp chatroom.jar chatroom.client.UserNode 1 Lucy
```

### On Machine 2 (User Node 2):
```bash
java -cp chatroom.jar chatroom.client.UserNode 2 Joel
```

---

## Step 4 – Use the Chat Room

```
Lucy_node> view
(no messages yet)

Lucy_node> post Welcome to the team project
[DME] Requesting critical section...
[DME] Critical section ACQUIRED. Posting...
Posted: 12 Oct 9:01AM Lucy: Welcome to the team project
[DME] Critical section RELEASED.

Joel_node> post Thanks Lucy - hope to work together
[DME] Requesting critical section...
[DME] Critical section ACQUIRED. Posting...
Posted: 12 Oct 9:04AM Joel: Thanks Lucy - hope to work together
[DME] Critical section RELEASED.

Joel_node> view
--- Chat Room Messages ---
12 Oct 9:01AM Lucy: Welcome to the team project
12 Oct 9:04AM Joel: Thanks Lucy - hope to work together
--------------------------
```

---

## How Lamport's DME Works (Algorithm Walkthrough)

### Normal case – Node 1 wants to POST:
1. Node 1 increments its Lamport clock → ts=5
2. Node 1 adds `(5, 1)` to its own priority queue
3. Node 1 sends `REQUEST(5, 1)` to Node 2
4. Node 2 receives REQUEST, updates clock, adds `(5,1)` to its queue, sends `REPLY(ts, 2)` back
5. Node 1 receives REPLY from Node 2
6. Node 1 checks: own request is at queue head + got REPLY from all peers → **enters CS**
7. Node 1 posts to file server
8. Node 1 sends `RELEASE(ts, 1)` to Node 2
9. Node 2 removes `(5,1)` from its queue

### Concurrent case – Both nodes request at same time:
- Both send REQUEST simultaneously
- Each receives the other's REQUEST → sends REPLY back
- Priority queue ordering: the node with **lower timestamp** (or lower ID on tie) wins
- The "winner" enters CS first; the other waits until RELEASE arrives

### Log evidence of DME working:
Look for lines like:
```
[DME][Node 1] REQUESTING CS at ts=5
[DME][Node 2] Received REQUEST from 1 ts=5
[DME][Node 1] Received REPLY from 2
[DME][Node 1] ENTERING CS
[DME][Node 1] RELEASING CS at ts=7
[DME][Node 2] Received RELEASE from 1
```

---

## Test Cases

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Single user posts a message then views | Message appears in view output |
| 2 | Two users view simultaneously | Both see identical file contents — no blocking |
| 3 | Two users post concurrently | Posts happen one after another, never overlapping; both messages saved |
| 4 | One user posts while another is posting | Second waits until first releases CS; verified via logs |
| 5 | User views after concurrent posts | All messages present, no corruption |
