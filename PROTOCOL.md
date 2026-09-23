# PROTOCOL — frozen contract

Everything in this file is agreed by all four pairs before coding starts.
Changing anything here after the freeze goes through A1 and gets announced
to the whole team — a silent change here breaks someone else's module.

Frozen: <FILL IN DATE / DAY-1 MEETING>

## 1. Wire payload shapes (JSON over HTTP)

### POST /api/chat
```json
{"sender_id": 1, "text": "Hello", "lamport": 4, "vector": [1, 4, 2]}
```

### POST /api/token
```json
{"token_holder": 1, "scores": {"alice": 42, "bob": 17}}
```

### POST /api/election
```json
{"type": "ELECTION", "sender_id": 2}
```
`type` is one of `"ELECTION"`, `"OK"`, `"COORDINATOR"`. For `COORDINATOR`,
`sender_id` carries the new leader's node ID.

### GET /api/health
No body. Response:
```json
{"status": "ALIVE"}
```

## 2. Class signatures

### `models.Message` (owned by Pair B)
```java
public class Message {
    public final int senderId;
    public final String text;
    public final int lamportTime;
    public final int[] vectorClock;
    public final long receivedAtNanos; // for tie-breaking / debugging only

    public Message(int senderId, String text, int lamportTime, int[] vectorClock) { ... }
}
```
- Total order comparator: `(lamportTime, senderId)` ascending
- Causal comparison: standard vector `happensBefore` / `isConcurrentWith`

### `models.Clock` (owned by Pair B)
Signatures as given in the skeleton — do not change the public method
signatures (`tick()`, `updateOnReceive(int, int[])`, `getLamportTime()`,
`getVectorClock()`), since `ChatHandler` and `Node` are written against them.

### `sync.MutualExclusion` (owned by Pair C)
Signatures as given in the skeleton
(`requestCriticalSection()`, `receiveToken()`). If you add a callback for
"critical section entered/exited" for logging, name it
`onCriticalSectionEnter()` / `onCriticalSectionExit()` and tell A1 — Node.java
may need to wire it to the logger.

### `sync.Election` (owned by Pair D)
Signatures as given in the skeleton
(`startElection()`, `handleElectionMessage(int)`,
`handleCoordinatorMessage(int)`). Election needs a way to *send* HTTP
messages to peers — use `api.NetworkClient`, do not create a second HTTP
client.

### `api.NetworkClient` (owned by Pair A2, starter provided)
```java
public class NetworkClient {
    public NetworkClient(int selfNodeId);
    public CompletableFuture<Integer> post(int port, String path, String jsonBody);
    public boolean getHealth(int port); // blocking, short timeout, false on any failure
}
```

### `api.Json` (owned by Pair A2, starter provided)
```java
public class Json {
    public static Object parse(String json);           // Map / List / String / Double / Boolean / null
    public static String stringify(Object value);       // inverse of parse
}
```
Treat numbers as `Double` after parsing; cast down with `.intValue()` where
you need an `int`.

## 3. Logging format (everyone uses this — do not roll your own)

```
[nodeId] LAMPORT=<n> VECTOR=<[...]> <EVENT_TAG> <free text>
```
Example:
```
[2] LAMPORT=5 VECTOR=[1,3,5] CHAT_RECV from=1 text="hello"
[0] LAMPORT=0 VECTOR=[0,0,0] TOKEN_PASS to_port=8001
[1] LAMPORT=0 VECTOR=[0,0,0] ELECTION_START
```
Use `Log.event(nodeId, clock, tag, message)` (in `api` package, A2 to add)
rather than raw `System.out.println`, so every log line is this shape.
This is what makes the "test proof logs" section of the report easy to
write — grep by `EVENT_TAG` and you have your evidence.

## 4. Command-line contract for Node.java

```
java -cp out Node <nodeId> <portBase> <totalNodes>
```
Peer for node `i` is on port `portBase + i`. Next-in-ring peer for
mutual exclusion is `portBase + ((nodeId + 1) % totalNodes)`.

## 5. Change process

1. Propose the change in the team chat with the reason
2. A1 confirms which other modules it touches
3. Update this file in the same PR as the code change
4. A1 announces it once merged
