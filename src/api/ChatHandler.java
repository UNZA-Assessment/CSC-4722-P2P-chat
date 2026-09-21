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

        // TODO (Pair B): extract sender_id, text, lamport, vector from payload
        // TODO (Pair B): clock.updateOnReceive(lamport, vector)
        // TODO (Pair B): build a Message and insert into the sorted log
        // TODO (Pair B): Log.event(nodeId, clock, "CHAT_RECV", ...) using the
        //                shared logging format in PROTOCOL.md

        sendResponse(exchange, 200, "{\"status\":\"Message Received\"}");
    }

    // --- Owned by Pair C (mutual exclusion) ---
    private void handleToken(HttpExchange exchange) throws IOException {
        String body = readBody(exchange); // TODO (Pair C): parse token_holder / scores payload
        mutex.receiveToken();
        sendResponse(exchange, 200, "{\"status\":\"Token Handled\"}");
    }

    // --- Owned by Pair D (election) ---
    @SuppressWarnings("unchecked")
    private void handleElection(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> payload = (Map<String, Object>) Json.parse(body);

        // TODO (Pair D): read "type" and "sender_id" from payload
        // TODO (Pair D): dispatch to election.handleElectionMessage(senderId)
        //                or election.handleCoordinatorMessage(senderId)
        //                based on "type". Reply immediately - do NOT run
        //                the election logic on this request thread; hand
        //                it to a worker so this handler returns fast.

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
