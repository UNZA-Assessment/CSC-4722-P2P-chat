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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Owned by Pair A (routing) - business logic in each branch is filled in
 * by the owning pair (marked below). Do not restructure the route
 * dispatch without telling A1; other pairs' branches depend on this shape.
 */
public class ChatHandler implements HttpHandler {

    private final Clock clock;
    private final MutualExclusion mutex;
    private final Election election;
    private final int nodeId;
    private final List<Message> messageLog = Collections.synchronizedList(new ArrayList<>());

    public ChatHandler(Clock clock, MutualExclusion mutex, Election election, int nodeId) {
        this.clock = clock;
        this.mutex = mutex;
        this.election = election;
        this.nodeId = nodeId;
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

    // --- Owned by Pair B (clocks / message log) ---
    @SuppressWarnings("unchecked")
    private void handleChat(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Object parsed = Json.parse(body);
        Map<String, Object> payload = (Map<String, Object>) parsed;

        int senderId = ((Number) payload.get("sender_id")).intValue();
        String text = (String) payload.get("text");
        int lamport = ((Number) payload.get("lamport")).intValue();

        List<?> rawVector = (List<?>) payload.get("vector");
        int[] vector = new int[rawVector.size()];
        for (int i = 0; i < rawVector.size(); i++) {
            vector[i] = ((Number) rawVector.get(i)).intValue();
        }

        // Update logical clock state upon receiving message
        clock.updateOnReceive(lamport, vector);

        // Store message in sorted log
        synchronized (messageLog) {
            messageLog.add(new Message(senderId, text, lamport, vector));
            messageLog.sort((m1, m2) -> Integer.compare(m1.lamportTime, m2.lamportTime));
        }

        sendResponse(exchange, 200, "{\"status\":\"Message Received\"}");
    }

    // --- Owned by Pair C (mutual exclusion) ---
    private void handleToken(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        mutex.receiveToken();
        sendResponse(exchange, 200, "{\"status\":\"Token Handled\"}");
    }

    // --- Owned by Pair D (election) ---
    @SuppressWarnings("unchecked")
    private void handleElection(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> payload = (Map<String, Object>) Json.parse(body);

        String type = (String) payload.get("type");
        int senderId = ((Number) payload.get("sender_id")).intValue();

        // Dispatch election handling to a background thread so the HTTP handler returns immediately
        new Thread(() -> {
            if ("ELECTION".equals(type)) {
                election.handleElectionMessage(senderId);
            } else if ("COORDINATOR".equals(type)) {
                election.handleCoordinatorMessage(senderId);
            }
        }).start();

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