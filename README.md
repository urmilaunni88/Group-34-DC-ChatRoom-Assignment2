# 💬 Group-34-DC-ChatRoom-Assignment2

<p align="center">
  <img src="https://img.shields.io/badge/Language-Java%2011%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Algorithm-Lamport's%20DME-4B8BBE?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Nodes-3%20Node%20System-2ea44f?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Course-CCZG%20526-purple?style=for-the-badge"/>
</p>

<p align="center">
  A 3-node distributed chat room where team members can exchange messages via a shared file.<br/>
  Write access is controlled using <b>Lamport's Distributed Mutual Exclusion algorithm</b> — no central coordinator.
</p>

---

## 📖 Table of Contents

- [Introduction](#-introduction)
- [Problem Statement](#-problem-statement)
- [System Architecture](#-system-architecture)
- [Project Structure](#-project-structure)
- [How Lamport's DME Works](#-how-lamports-dme-works)
- [Prerequisites](#-prerequisites)
- [Configuration](#%EF%B8%8F-configuration)
- [Build & Run — 3 Machines](#-build--run-3-machines)
- [Run on 1 Machine with IntelliJ](#-run-on-1-machine-with-intellij)
- [CLI Commands](#-cli-commands)
- [Sample Chat Session](#-sample-chat-session)
- [DME Log Evidence](#-dme-log-evidence)
- [Test Cases](#-test-cases)
- [Additional Features](#-additional-features)
- [Group Contributions](#-group-contributions)
- [References](#-references)

---

## 📝 Introduction

This assignment implements a **Distributed Chat Room** application as part of the CCZG 526 Distributed Computing course. The system simulates a collaborative team environment where multiple users, running on separate nodes, can read and write messages to a shared file over a network.

The system consists of three nodes:
- **Node 0** acts as the File Server — stores and manages the shared chat file (`chatroom.txt`)
- **Node 1 and Node 2** are User Nodes — allow users to interact through a command-line interface

Users can perform two operations:
- **`view`** — reads all existing messages from the shared file (concurrent access allowed)
- **`post`** — appends a new message with the user's name and timestamp (requires mutual exclusion)

The key challenge is **concurrent write access** — when two users attempt to post at the same time, the shared file could get corrupted or messages could be lost. To solve this, we implemented **Lamport's Distributed Mutual Exclusion (DME) Algorithm** as a dedicated middleware module, completely separate from the application layer.

The implementation is in **Java**, using TCP sockets for all inter-node communication. The codebase is divided into two clear modules — the DME middleware (`dme` package) and the collaboration application (`server` and `client` packages).

---

## 📋 Problem Statement

Build a **3-node distributed system** implementing a team chat room:

- Messages are stored in a **single shared file** on a server node
- Any user can **`view`** messages at any time — concurrent reads allowed
- Only **one user** can **`post`** at a time — enforced by distributed mutual exclusion
- Each post records the **client-side timestamp**, username, and text
- The **DME middleware** and the **chat application** must be kept in separate modules

---

## 🏗 System Architecture

```
          ┌──────────────────────────────────────────────┐
          │               DISTRIBUTED SYSTEM             │
          │                                              │
          │    ┌───────────────┐   ┌───────────────┐    │
          │    │   Node 1      │   │   Node 2      │    │
          │    │  (User Node)  │◄─►│  (User Node)  │    │
          │    │   Port 5001   │   │   Port 5002   │    │
          │    │               │   │               │    │
          │    │  ┌──────────┐ │   │ ┌──────────┐  │    │
          │    │  │LamportDME│ │   │ │LamportDME│  │    │
          │    │  │Middleware│ │   │ │Middleware│  │    │
          │    │  └──────────┘ │   │ └──────────┘  │    │
          │    │  ┌──────────┐ │   │ ┌──────────┐  │    │
          │    │  │UserNode  │ │   │ │UserNode  │  │    │
          │    │  │   CLI    │ │   │ │   CLI    │  │    │
          │    │  └──────────┘ │   │ └──────────┘  │    │
          │    └──────┬────────┘   └───────┬───────┘    │
          │           │   VIEW / POST       │            │
          │           └─────────┬───────────┘            │
          │                     ▼                        │
          │          ┌──────────────────┐                │
          │          │     Node 0       │                │
          │          │   File Server    │                │
          │          │   Port  5000     │                │
          │          │  chatroom.txt    │                │
          │          └──────────────────┘                │
          └──────────────────────────────────────────────┘

  DME messages (REQUEST / REPLY / RELEASE) → between Node 1 and Node 2
  File operations (VIEW / POST)            → from Node 1/2 to Node 0
```

| Node   | Role                       | Port |
|--------|----------------------------|------|
| Node 0 | File Server (shared file)  | 5000 |
| Node 1 | User Node + DME Listener   | 5001 |
| Node 2 | User Node + DME Listener   | 5002 |

---

## 📁 Project Structure

```
chatroom/
├── build.sh
├── README.md
└── src/
    └── main/
        └── java/
            └── chatroom/
                ├── common/
                │   ├── Config.java        # IP addresses, ports, shared file name
                │   └── Message.java       # Serializable message protocol
                ├── dme/                   # MODULE 1: DME Middleware
                │   ├── LamportClock.java  # Thread-safe Lamport logical clock
                │   └── LamportDME.java    # Lamport's mutual exclusion algorithm
                ├── server/                # MODULE 2: File Server
                │   └── FileServer.java    # Handles VIEW and POST requests
                └── client/               # MODULE 3: Chat Application
                    └── UserNode.java      # CLI + calls DME middleware before writes
```

> Two separate modules as required:
> `dme/` = pure distributed middleware.
> `client/` + `server/` = collaboration application that calls the middleware.

---

## 🔐 How Lamport's DME Works

### The Three Message Types

| Message   | When Sent                        | Effect on Receiver                               |
|-----------|----------------------------------|--------------------------------------------------|
| `REQUEST` | When a node wants to write       | Receiver adds to queue, sends REPLY back         |
| `REPLY`   | In response to a REQUEST         | Sender records that this peer has granted access |
| `RELEASE` | After finishing the write        | Receiver removes sender's entry from queue       |

### Entry Condition — Both Must Be True

```
1. Own REQUEST is at the HEAD of the priority queue
                  AND
2. A REPLY has been received from ALL other nodes
```

### Priority Queue Ordering

Requests sorted by `(Lamport timestamp, node ID)` — ties broken by node ID, ensuring no starvation.

### Concurrent POST — Step by Step

```
Time    Node 1 (Alice)                     Node 2 (Bob)
t=1     clock=5, adds (5,1) to queue
        broadcasts REQUEST(5,1) ─────────────────────────────►
                                           clock=5, adds (5,2) to queue
                                ◄──────────── broadcasts REQUEST(5,2)

t=2     receives REQUEST(5,2)              receives REQUEST(5,1)
        adds (5,2) to queue                adds (5,1) to queue
        sends REPLY ─────────────────────────────────────────►
                                ◄──────────────────── sends REPLY

t=3     Queue: [(5,1),(5,2)]              Queue: [(5,1),(5,2)]
        Head=(5,1)==self  YES             Head=(5,1) not self  NO
        Has reply from Node 2  YES        WAITS
        ENTERS CS
        ... writes to file server ...

t=4     sends RELEASE ───────────────────────────────────────►
                                           removes (5,1) from queue
                                           Queue: [(5,2)]
                                           Head=(5,2)==self  YES
                                           ENTERS CS
                                           ... writes to file server ...
```

Result: Both messages saved safely, in order, with zero corruption.

---

## ✅ Prerequisites

| Tool     | Version | Notes                      |
|----------|---------|----------------------------|
| Java JDK | 11+     | Needed to compile (build)  |
| Java JRE | 11+     | Needed to run on all nodes |
| bash     | any     | To run build.sh            |

Verify your setup:
```bash
java -version
javac -version
```

---

## ⚙️ Configuration

Edit `src/main/java/chatroom/common/Config.java` before building:

```java
// Set these to your actual cloud machine IPs
public static final String SERVER_HOST = "10.0.0.1";  // Node 0
public static final String NODE1_HOST  = "10.0.0.2";  // Node 1
public static final String NODE2_HOST  = "10.0.0.3";  // Node 2

// Ports (open these in your cloud firewall)
public static final int SERVER_FILE_PORT = 5000;
public static final int NODE1_DME_PORT   = 5001;
public static final int NODE2_DME_PORT   = 5002;
```

For single-machine / IntelliJ testing, all hosts default to `"localhost"` — no change needed.

---

## 🚀 Build & Run — 3 Machines

### 1. Build the JAR

```bash
cd chatroom/
chmod +x build.sh
./build.sh
```

### 2. Copy JAR to all 3 machines

```bash
scp chatroom.jar user@<node0-ip>:~/
scp chatroom.jar user@<node1-ip>:~/
scp chatroom.jar user@<node2-ip>:~/
```

### 3. Start Node 0 — File Server (start this FIRST)

```bash
java -cp chatroom.jar chatroom.server.FileServer
```

### 4. Start Node 1

```bash
java -cp chatroom.jar chatroom.client.UserNode 1 Alice
```

### 5. Start Node 2

```bash
java -cp chatroom.jar chatroom.client.UserNode 2 Bob
```

> Open ports 5000, 5001, 5002 in your cloud firewall before running.

---

## 💻 Run on 1 Machine with IntelliJ

### Step 1 — Mark sources root
Right-click `src/main/java` → Mark Directory As → Sources Root

### Step 2 — Create 3 Run Configurations
Run → Edit Configurations → + → Application

| Config Name   | Main Class                   | Program Arguments |
|---------------|------------------------------|-------------------|
| `FileServer`  | `chatroom.server.FileServer` | (leave empty)     |
| `Node1-Alice` | `chatroom.client.UserNode`   | `1 Alice`         |
| `Node2-Bob`   | `chatroom.client.UserNode`   | `2 Bob`           |

### Step 3 — Enable parallel run
For Node1 and Node2 configs: tick **Allow parallel run**

### Step 4 — Run in order
FileServer → Node1-Alice → Node2-Bob

---

## 🖥 CLI Commands

| Command       | Description                                      | Mutex Needed? |
|---------------|--------------------------------------------------|---------------|
| `view`        | Fetch and display all messages                   | No            |
| `view <N>`    | Fetch and display only the last N messages       | No            |
| `post <text>` | Append a new message (timestamp + username)      | Yes (DME)     |
| `quit`        | Exit the node cleanly                            | —             |

---

## 📟 Sample Chat Session

```
Alice_node> view
(no messages yet)

Alice_node> post Hello everyone this is Alice
[DME] Requesting critical section...
[DME] Critical section ACQUIRED. Posting...
Posted: 28 Apr 10:45AM Alice: Hello everyone this is Alice
[DME] Critical section RELEASED.

Bob_node> post Hi Alice, ready to work!
[DME] Requesting critical section...
[DME] Critical section ACQUIRED. Posting...
Posted: 28 Apr 10:47AM Bob: Hi Alice, ready to work!
[DME] Critical section RELEASED.

Bob_node> view
--- Chat Room Messages ---
28 Apr 10:45AM Alice: Hello everyone this is Alice
28 Apr 10:47AM Bob: Hi Alice, ready to work!
--------------------------
```

---

## 📊 DME Log Evidence

```
[DME][Node 1] REQUESTING CS at ts=6
[DME][Node 2] Received REQUEST from 1 ts=6
[DME][Node 1] Received REPLY from 2
[DME][Node 1] ENTERING CS
   ... file server write happens here ...
[DME][Node 1] RELEASING CS at ts=12
[DME][Node 2] Received RELEASE from 1
[DME][Node 2] ENTERING CS
```

### Three Guarantees Proven by the Logs

| Property     | What It Means                            | Evidence                                                 |
|--------------|------------------------------------------|----------------------------------------------------------|
| **Safety**   | At most one node in CS at any time       | ENTERING CS lines never overlap in timestamps            |
| **Liveness** | Every request eventually gets the CS     | Every REQUESTING is always followed by ENTERING          |
| **Fairness** | Requests served in timestamp order       | Lower timestamp always enters CS first                   |

---

## 🧪 Test Cases

| # | Scenario                          | Expected Result                                       |
|---|-----------------------------------|-------------------------------------------------------|
| 1 | Single user posts then views      | Message appears in subsequent view                    |
| 2 | Both users view simultaneously    | Both see same content with no blocking                |
| 3 | Both users post at the same time  | One waits; both messages saved in order, no overlap   |
| 4 | User 1 posts while User 2 is mid-post | User 1 blocks until User 2 releases CS            |
| 5 | view 3                            | Only last 3 messages shown with count of hidden ones  |

---


---


**Language used:** Java

---

