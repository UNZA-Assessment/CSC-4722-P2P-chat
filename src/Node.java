import com.sun.net.httpserver.HttpServer;
import api.ChatHandler;
import api.NetworkClient;
import models.Clock;
import sync.Election;
import sync.ElectionFailureHandler;
import sync.MutualExclusion;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owned by Pair A (A1).
 *
 * Fixes vs the original skeleton:
 *  - portBase / totalNodes are CLI args, not hardcoded to 8000-8009.
 *  - server.setExecutor(null) is replaced with a fixed thread pool.
 *    (setExecutor(null) makes HttpServer handle one request at a time,
 *    which deadlocks the moment a handler makes a blocking outbound
 *    HTTP call - e.g. token forwarding or election OK replies.)
 *  - a shutdown hook stops the server and pool cleanly, so kill_all.sh
 *    and the leader-failure demo don't leave sockets in TIME_WAIT.
 *  - a background health poller watches the current leader and calls
 *    election.startElection() after repeated missed health checks.
 */
public class Node {

    private static final long POLL_INTERVAL_MS = 1000;
    private static final int FAILURE_THRESHOLD = 3; // consecutive misses before triggering election

    // Multiple nodes CAN share one physical host (different ports, same
    // machine) - that's a legitimate setup when you have fewer laptops
    // than node IDs. This ceiling only exists to catch a genuine config
    // mistake (e.g. accidentally pointing every port at one IP by typo),
    // not to forbid intentional doubling-up. Raise it if your real
    // hardware layout needs more nodes on one machine than this.
    private static final int MAX_NODES_PER_HOST = 4;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java Node <nodeId> <portBase> <totalNodes>");
            System.exit(1);
        }

        int nodeId = Integer.parseInt(args[0]);
        int portBase = Integer.parseInt(args[1]);
        int totalNodes = Integer.parseInt(args[2]);

        if (nodeId < 0 || nodeId >= totalNodes) {
            System.out.println("nodeId must be in [0, totalNodes)");
            System.exit(1);
        }

        int port = portBase + nodeId;

        List<Integer> peerPorts = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) {
            peerPorts.add(portBase + i);
        }
        int nextPeerPort = peerPorts.get((nodeId + 1) % totalNodes);

        Map<Integer, String> peerHosts = parsePeerHosts(System.getenv("P2P_PEERS"));
        validateUniquePeerHosts(peerHosts, peerPorts);
        String hostName = InetAddress.getLocalHost().getHostName();
        NetworkClient networkClient = new NetworkClient(nodeId, peerHosts);
        Clock clock = new Clock(nodeId, totalNodes);
        MutualExclusion mutex = new MutualExclusion(nodeId, nextPeerPort, nodeId == 0, networkClient);
        Election election = new Election(nodeId, peerPorts, networkClient);
        ElectionFailureHandler failureHandler = new ElectionFailureHandler(election, networkClient);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ChatHandler(clock, mutex, election, nodeId,
            networkClient, peerPorts, hostName, port));

        // Fixed pool instead of the single-threaded default executor.
        // Size is generous for a course project; tune if needed.
        ExecutorService pool = Executors.newFixedThreadPool(16);
        server.setExecutor(pool);

        server.start();
        System.out.println("Node " + nodeId + " running on port " + port
                + " (peers: " + peerPorts + ", next-in-ring: " + nextPeerPort + ")");

        // The node uses a single leader-health monitor so that election
        // detection and triggering are consistent across the process.
        Thread healthPoller = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                    failureHandler.checkLeaderHealth();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "health-poller-" + nodeId);
        healthPoller.setDaemon(true);
        healthPoller.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Node " + nodeId + " shutting down...");
            healthPoller.interrupt();
            server.stop(0);
            pool.shutdown();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    private static Map<Integer, String> parsePeerHosts(String value) {
        Map<Integer, String> peerHosts = new HashMap<>();
        if (value == null || value.isBlank()) {
            return peerHosts;
        }
        for (String entry : value.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length == 2 && !parts[1].isBlank()) {
                peerHosts.put(Integer.parseInt(parts[0].trim()), parts[1].trim());
            }
        }
        return peerHosts;
    }

    private static void validateUniquePeerHosts(Map<Integer, String> peerHosts, List<Integer> peerPorts) {
        if (peerHosts.isEmpty()) {
            return;
        }

        Map<String, List<Integer>> hostToPorts = new HashMap<>();
        for (int port : peerPorts) {
            String host = peerHosts.get(port);
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Missing P2P_PEERS entry for port " + port);
            }
            hostToPorts.computeIfAbsent(host, h -> new ArrayList<>()).add(port);
        }

        for (Map.Entry<String, List<Integer>> entry : hostToPorts.entrySet()) {
            String host = entry.getKey();
            List<Integer> ports = entry.getValue();

            if (ports.size() > 1) {
                // Soft warning only - this is a normal setup when there are
                // fewer physical machines than nodes. Printed so it's easy
                // to confirm the layout was intentional when reading logs.
                System.out.println("[startup] NOTE: " + host + " is hosting " + ports.size()
                        + " nodes on ports " + ports + ". This is fine if intentional"
                        + " (e.g. limited hardware); each still runs as an independent"
                        + " process on its own port.");
            }

            if (ports.size() > MAX_NODES_PER_HOST) {
                // Hard stop - this many nodes on one host is far more
                // likely to be a config mistake (e.g. every port
                // accidentally pointed at the same IP) than a deliberate
                // choice. Raise MAX_NODES_PER_HOST above if your hardware
                // genuinely needs more.
                throw new IllegalArgumentException(host + " is hosting " + ports.size()
                        + " nodes (ports " + ports + "), which exceeds the safety ceiling of "
                        + MAX_NODES_PER_HOST + " nodes per host. If this is really intended,"
                        + " raise MAX_NODES_PER_HOST in Node.java.");
            }
        }
    }
}