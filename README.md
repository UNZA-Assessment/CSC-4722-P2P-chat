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
| C — Mutual Exclusion | C1, C2 | `MutualExclusion.java`, scoreboard, ring repair |
| D — Election | D1, D2 | `Election.java`, failure detector, `/api/election` |

See `PROTOCOL.md` for the frozen contract (payload shapes, class signatures)
that all four pairs build against. **Do not change it without asking A1.**

## Status of files in this scaffold

- ✅ **Done** — `Node.java`, `start_all.sh`, `kill_all.sh`, `ChatHandler.java` (routing only)
- 🟡 **Starter, needs refinement by owning pair** — `Json.java`, `NetworkClient.java` (A2)
- ⬜ **Stub / TODO for owning pair** — `Clock.java` (B), `Message.java` (B),
  `MutualExclusion.java` (C), `Election.java` (D)

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
- `test_mutex.sh` (Pair C) — several nodes request the critical section at
  once; proves non-overlapping CS intervals from logs
- `test_election.sh` (Pair D) — kills the current leader, captures the
  ELECTION → OK → COORDINATOR sequence across surviving nodes

## Branching & merge process

- Branch names: `core/...`, `clocks/...`, `mutex/...`, `election/...`
- Open a PR into `main`; A1 reviews and runs the smoke test before merging
- No direct pushes to `main` (branch protection is on)
- Changes to `PROTOCOL.md` go through A1 and get announced to all four pairs
