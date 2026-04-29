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

## 10-Minute Video Demo Script (5 Members)

### [0:00 – 1:30] Member 1 – Introduction & Problem Statement
**Says:**
> "Hello everyone. This is our demonstration for DC Assignment II — a distributed chat room application.
> The problem requires a 3-node distributed system: one file server node and two user nodes. 
> Multiple users can view messages at any time, but only ONE user can post at a time — enforced 
> by Lamport's Distributed Mutual Exclusion algorithm. Let me hand over to [Member 2] to walk 
> through the system design."

### [1:30 – 3:00] Member 2 – Architecture & Code Structure
**Says:**
> "We have three separate Java modules. First, the **FileServer** — it runs on Node 0 and simply 
> stores the shared chatroom.txt file. It exposes two operations over TCP: VIEW, which reads the 
> file, and POST, which appends a new message. 
>
> Second, the **LamportDME module** — this is our distributed middleware. It implements Lamport's 
> 1978 algorithm using REQUEST, REPLY, and RELEASE messages and a priority queue ordered by 
> logical timestamps.
>
> Third, the **UserNode application** — this is what each user runs. It starts the DME listener, 
> accepts CLI commands, and calls the DME layer before any write."

### [3:00 – 5:00] Member 3 – Start All Nodes (Live Demo)
**Shows & says:**
> "Now let's start the system. [Open terminal on Machine 0] I'm starting the file server first:
> `java -cp chatroom.jar chatroom.server.FileServer` — you can see it's listening on port 5000.
>
> [Open terminal on Machine 1] Now Node 1 with username Lucy:
> `java -cp chatroom.jar chatroom.client.UserNode 1 Lucy`
>
> [Open terminal on Machine 2] And Node 2 with username Joel:
> `java -cp chatroom.jar chatroom.client.UserNode 2 Joel`
>
> All three nodes are up. Notice each user node also started its DME listener port."

### [5:00 – 7:30] Member 4 – Live Chat + DME in Action
**Shows & says:**
> "Let's demonstrate normal operation first. Lucy types `view` — you see 'no messages yet'.
> Now Lucy types `post Welcome to the team project`. Watch the DME log: 
> 'REQUESTING CS', then 'ACQUIRED', then 'RELEASED' — that's Lamport's algorithm working.
>
> Now I'll demonstrate CONCURRENT access — the interesting case. [Type post on both nodes 
> simultaneously] Both nodes request the CS at the same time. Look at the logs — both nodes 
> exchange REQUEST and REPLY messages. The node with the lower Lamport timestamp wins and 
> enters the CS first. The other node waits. You can see them enter the CS one after the other 
> — never simultaneously. That's mutual exclusion being enforced."

### [7:30 – 10:00] Member 5 – Log Analysis + Summary
**Says:**
> "Let me highlight the log evidence that proves DME is working correctly. 
> [Point to log lines] You can see: REQUEST sent at timestamp 5, REPLY received from peer, 
> ENTERING CS, post completes, RELEASING CS, RELEASE message received at the other node.
>
> Key properties satisfied:
> — **Safety**: Only one node in CS at a time — confirmed by logs showing no overlap.
> — **Liveness**: Every request eventually gets the CS — no deadlock.  
> — **Fairness**: Requests served in Lamport timestamp order.
>
> This is a fully non-centralised solution using Lamport's algorithm — we have no central 
> coordinator deciding access. The nodes coordinate purely by message passing.
>
> Thank you for watching our demo. The code has two separate modules — DME middleware and 
> the chat application — as required. All source files are in the submission."

---

## Test Cases

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Single user posts a message then views | Message appears in view output |
| 2 | Two users view simultaneously | Both see identical file contents — no blocking |
| 3 | Two users post concurrently | Posts happen one after another, never overlapping; both messages saved |
| 4 | One user posts while another is posting | Second waits until first releases CS; verified via logs |
| 5 | User views after concurrent posts | All messages present, no corruption |
