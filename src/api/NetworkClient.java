package api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Owned by Pair A2. STARTER VERSION.
 *
 * Every outbound call has a short timeout so that a dead peer never hangs
 * a request handler thread forever - this is what makes ring-repair and
 * failure detection possible without both blocking indefinitely.
 *
 * post() is ASYNC on purpose: callers like MutualExclusion.passToken()
 * must not block while holding a lock. getHealth() is a small, deliberately
 * blocking convenience call with its own short timeout, meant to be used
 * from a background poller thread, not from inside a synchronized method.
 */
public class NetworkClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(800);

    private final int selfNodeId;
    private final Map<Integer, String> peerHosts;
    private final HttpClient client;

    public NetworkClient(int selfNodeId) {
        this(selfNodeId, new HashMap<>());
    }

    public NetworkClient(int selfNodeId, Map<Integer, String> peerHosts) {
        this.selfNodeId = selfNodeId;
        this.peerHosts = new HashMap<>(peerHosts);
        this.client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    private String hostFor(int port) {
        String host = peerHosts.get(port);
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Missing P2P_PEERS entry for port " + port);
        }
        return host;
    }

    public CompletableFuture<Integer> post(int port, String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + hostFor(port) + ":" + port + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(HttpResponse::statusCode)
                .exceptionally(ex -> {
                    System.out.println("[" + selfNodeId + "] POST to port " + port
                            + path + " failed: " + ex.getMessage());
                    return -1;
                });
    }

    /**
     * Blocking health check with a short timeout. Returns false on any
     * failure (connection refused, timeout, non-200) - callers should treat
     * false as "treat this peer as dead" rather than retrying inline.
     */
    public boolean getHealth(int port) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + hostFor(port) + ":" + port + "/api/health"))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
