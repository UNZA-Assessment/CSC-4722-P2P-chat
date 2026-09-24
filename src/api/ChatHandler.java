package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import models.Clock;
import models.Message;
import sync.Election;
import sync.MutualExclusion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owned by Pair A (routing) - business logic in each branch is filled in
 * by the owning pair (marked below). Do not restructure the route
 * dispatch without telling A1; other pairs' branches depend on this shape.
 */
public class ChatHandler implements HttpHandler {

    private static final List<Message> MESSAGE_LOG = new ArrayList<>();

    private final Clock clock;
    private final MutualExclusion mutex;
    private final Election election;
    private final int nodeId;
    private final NetworkClient networkClient;
    private final List<Integer> peerPorts;

    public ChatHandler(Clock clock, MutualExclusion mutex, Election election, int nodeId) {
        this(clock, mutex, election, nodeId, null, new ArrayList<>());
    }

    public ChatHandler(Clock clock, MutualExclusion mutex, Election election, int nodeId,
                       NetworkClient networkClient, List<Integer> peerPorts) {
        this.clock = clock;
        this.mutex = mutex;
        this.election = election;
        this.nodeId = nodeId;
        this.networkClient = networkClient;
        this.peerPorts = new ArrayList<>(peerPorts);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("POST".equals(method) && "/api/chat".equals(path)) {
                handleChat(exchange);
            } else if ("POST".equals(method) && "/api/token".equals(path)) {
                handleToken(exchange);
            } else if ("POST".equals(method) && "/api/election".equals(path)) {
                handleElection(exchange);
            } else if ("GET".equals(method) && "/api/state".equals(path)) {
                int leaderId = election.getCurrentLeaderId();
                int tokenHolder = mutex.getCurrentTokenHolder();
                String scores = Json.stringify(mutex.getScoreboard());
                sendResponse(exchange, 200,
                        "{\"nodeId\":" + nodeId + ",\"leaderId\":" + leaderId
                                + ",\"tokenHolderId\":" + tokenHolder
                                + ",\"electionInProgress\":" + election.isElectionInProgress()
                                + ",\"scores\":" + scores + "}");
            } else if ("GET".equals(method) && "/api/messages".equals(path)) {
                sendResponse(exchange, 200, Json.stringify(messagePayload()));
            } else if ("GET".equals(method) && "/api/health".equals(path)) {
                sendResponse(exchange, 200, "{\"status\":\"ALIVE\"}");
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
            }
        } catch (Exception e) {
            // Robust error handling: never let a handler exception hang the
            // client with no response. Log it and return 500.
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private List<Map<String, Object>> messagePayload() {
        List<Map<String, Object>> messages = new ArrayList<>();
        synchronized (MESSAGE_LOG) {
            for (Message message : MESSAGE_LOG) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("senderId", message.senderId);
                payload.put("text", message.text);
                payload.put("lamport", message.lamportTime);
                payload.put("vector", message.vectorClock);
                payload.put("receivedAtNanos", message.receivedAtNanos);
                messages.add(payload);
            }
        }
        return messages;
    }

    // --- Owned by Pair B (clocks / message log) ---
    @SuppressWarnings("unchecked")
    private void handleChat(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Object parsed = Json.parse(body);
        Map<String, Object> payload = (Map<String, Object>) parsed;

        int senderId = ((Number) payload.get("sender_id")).intValue();
        String text = (String) payload.get("text");
        int lamport = ((Number) payload.get("lamport")).intValue();
        boolean relay = Boolean.TRUE.equals(payload.get("relay"));

        List<?> rawVector = (List<?>) payload.get("vector");
        int[] vector = new int[rawVector == null ? 0 : rawVector.size()];
        if (rawVector != null) {
            for (int i = 0; i < rawVector.size(); i++) {
                vector[i] = ((Number) rawVector.get(i)).intValue();
            }
        }

        clock.updateOnReceive(lamport, vector);

        Message message = new Message(senderId, text, lamport, vector);
        synchronized (MESSAGE_LOG) {
            MESSAGE_LOG.add(message);
            MESSAGE_LOG.sort(Message.BY_TOTAL_ORDER);
        }

        Log.event(nodeId, clock, "CHAT_RECV", "from=" + senderId + " text=\"" + text.replace("\"", "\\\"") + "\"");

        if (!relay && networkClient != null) {
            String forwarded = Json.stringify(Map.of(
                    "sender_id", senderId,
                    "text", text,
                    "lamport", lamport,
                    "vector", vector,
                    "relay", true));
            for (int peerPort : peerPorts) {
                if (peerPort != nodePort()) {
                    networkClient.post(peerPort, "/api/chat", forwarded);
                }
            }
        }

        sendResponse(exchange, 200, "{\"status\":\"Message Received\"}");
    }

    private int nodePort() {
        if (nodeId >= 0 && nodeId < peerPorts.size()) {
            return peerPorts.get(nodeId);
        }
        return -1;
    }

    // --- Owned by Pair D (mutual exclusion) ---
    @SuppressWarnings("unchecked")
    private void handleToken(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map)) {
            sendResponse(exchange, 400, "{\"error\":\"Token payload must be an object\"}");
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) parsed;
        Map<String, Integer> incomingScores = new HashMap<>();

        Object scores = payload.get("scores");
        if (scores instanceof Map) {
            Map<String, Object> scoreMap = (Map<String, Object>) scores;
            for (Map.Entry<String, Object> entry : scoreMap.entrySet()) {
                Object score = entry.getValue();
                if (score instanceof Number) {
                    incomingScores.put(entry.getKey(), ((Number) score).intValue());
                }
            }
        }

        mutex.receiveToken(incomingScores);
        sendResponse(exchange, 200, "{\"status\":\"Token Handled\"}");
    }

    // --- Owned by Pair C (election) ---
    @SuppressWarnings("unchecked")
    private void handleElection(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> payload = (Map<String, Object>) Json.parse(body);

        String type = (String) payload.get("type");
        int senderId = ((Double) payload.get("sender_id")).intValue();
        if ("ELECTION".equals(type)) {
            election.handleElectionMessage(senderId);
        } else if ("OK".equals(type)) {
            election.handleOkMessage(senderId);
        } else if ("COORDINATOR".equals(type)) {
            election.handleCoordinatorMessage(senderId);
        } else {
            sendResponse(exchange, 400, "{\"error\":\"Unknown election message type\"}");
            return;
        }

        sendResponse(exchange, 200, "{\"status\":\"OK\"}");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }
}
