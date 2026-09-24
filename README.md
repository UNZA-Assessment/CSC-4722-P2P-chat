# CSC 4722 — P2P Distributed Chat & Scoreboard System

Peer-to-peer chat and shared high-score system demonstrating logical clocks,
distributed mutual exclusion (token ring), and leader election (Bully
algorithm), built on `com.sun.net.httpserver.HttpServer` and
`java.net.http.HttpClient` — no external frameworks.

## Team & ownership

| Pair | Members | Owns |
|------|---------|------|
| A — Core & Infra | A1 (you), A2 | `Node.java`, `Json.java`, `NetworkClient.java`, logging format, `ChatHandler` routing skeleton |
| B — Clocks | B1, B2 | `Clock.java`, `Message.java`, log ordering |
| C — Election | C1, C2 | `Election.java`, failure detector, `/api/election` |
| D — Mutual Exclusion | D1, D2 | `MutualExclusion.java`, scoreboard, ring repair |

See `PROTOCOL.md` for the frozen contract (payload shapes, class signatures)
that all four pairs build against. **Do not change it without asking A1.**

## Status of files in this scaffold

- ✅ **Done** — `Node.java`, `start_all.sh`, `kill_all.sh`, `ChatHandler.java` (routing only)
- 🟡 **Starter, needs refinement by owning pair** — `Json.java`, `NetworkClient.java` (A2)
- ⬜ **Stub / TODO for owning pair** — `Clock.java` (B), `Message.java` (B),
  `MutualExclusion.java` (D), `Election.java` (C)

## Requirements

- Java 11+
- No external dependencies

## Project layout

```
src/
├── Node.java              // Main application runner
├── models/
│   ├── Message.java       // Chat message structure
│   └── Clock.java         // Lamport & Vector Clock logic
├── sync/
│   ├── MutualExclusion.java  // Token Ring critical section
│   └── Election.java         // Bully algorithm
└── api/
    ├── ChatHandler.java   // REST API endpoints
    ├── NetworkClient.java // HTTP POST/GET helper
    └── Json.java          // Hand-rolled JSON parse/stringify
```

## Building

```bash
javac -d out $(find src -name "*.java")
```

## Running a single node manually

```bash
java -cp out Node <nodeId> <portBase> <totalNodes>
# e.g. a 3-node local cluster, this is node 0 on port 8000:
java -cp out Node 0 8000 3
```

## Running the whole cluster

```bash
./start_all.sh 3        # launches nodes 0,1,2 on ports 8000-8002
                         # logs go to logs/node_0.log ... logs/node_2.log
./kill_all.sh            # stops everything cleanly
```

## Running nodes on different laptops

Put every laptop on the same trusted LAN, give each laptop a fixed/reachable
LAN address, and allow the node ports through its firewall. Use the same node
count, base port, and complete `P2P_PEERS` mapping on every laptop. The mapping
keys are node ports, not node IDs. The dashboard displays only peers listed in
this mapping; it does not scan or add localhost nodes:

```bash
export P2P_PEERS='8000=192.168.1.10,8001=192.168.1.11,8002=192.168.1.12'
```

Build the project on each laptop, then launch only that laptop's node. The
third argument selects the node ID while the first argument remains the total
cluster size:

```bash
# Laptop 192.168.1.10
./start_all.sh 3 8000 0

# Laptop 192.168.1.11
./start_all.sh 3 8000 1

# Laptop 192.168.1.12
./start_all.sh 3 8000 2
```

You can also use the single-node wrapper to avoid accidentally starting the
whole cluster on one laptop:

```bash
./start_node.sh <NODE_ID> <TOTAL_NODES> <PORT_BASE>
# example: this laptop runs only node 1 in a 10-node cluster
./start_node.sh 1 10 8000
```

Verify from each laptop that every peer is reachable:

```bash
curl http://192.168.1.10:8000/api/health
curl http://192.168.1.11:8001/api/health
curl http://192.168.1.12:8002/api/health
```

To run the dashboard on one laptop, use the same mapping and cluster size:

```bash
P2P_PEERS="$P2P_PEERS" P2P_BASE_PORT=8000 python3 ui/server.py
```

Open `http://<dashboard-laptop-ip>:8080/` from another laptop. Do not expose
these unauthenticated development endpoints to the public internet.

## Smoke test (run after every merge to main)

```bash
./start_all.sh 3
curl -s -X POST localhost:8000/api/chat \
  -d '{"sender_id":0,"text":"hello","lamport":1,"vector":[1,0,0]}'
curl -s localhost:8000/api/health
curl -s localhost:8001/api/health
curl -s localhost:8002/api/health
./kill_all.sh
```

All three health checks should return `{"status":"ALIVE"}` and the chat
POST should return `{"status":"Message Received"}`. If not, the merge
does not land — see PROTOCOL.md for the merge process.

## Demo scripts (add as each pair's module lands)

- `test_ordering.sh` (Pair B) — fires concurrent chat messages at multiple
  nodes, dumps each node's log to prove consistent ordering
- `test_mutex.sh` (Pair D) — several nodes request the critical section at
  once; proves non-overlapping CS intervals from logs
- `test_election.sh` (Pair C) — kills the current leader, captures the
  ELECTION → OK → COORDINATOR sequence across surviving nodes

## Branching & merge process

- Branch names: `core/...`, `clocks/...`, `mutex/...`, `election/...`
- Open a PR into `main`; A1 reviews and runs the smoke test before merging
- No direct pushes to `main` (branch protection is on)
- Changes to `PROTOCOL.md` go through A1 and get announced to all four pairs
