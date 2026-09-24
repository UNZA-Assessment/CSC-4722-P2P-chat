import com.sun.net.httpserver.HttpServer;
import api.ChatHandler;
import api.NetworkClient;
import models.Clock;
import sync.Election;
import sync.MutualExclusion;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
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
 */
public class Node {

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

        NetworkClient networkClient = new NetworkClient(nodeId);
        Clock clock = new Clock(nodeId, totalNodes);
        MutualExclusion mutex = new MutualExclusion(nodeId, nextPeerPort, nodeId == 0, networkClient);
        Election election = new Election(nodeId, peerPorts, networkClient);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ChatHandler(clock, mutex, election, nodeId));

        // Fixed pool instead of the single-threaded default executor.
        // Size is generous for a course project; tune if needed.
        ExecutorService pool = Executors.newFixedThreadPool(16);
        server.setExecutor(pool);

        server.start();
        election.startFailureDetector();
        System.out.println("Node " + nodeId + " running on port " + port
                + " (peers: " + peerPorts + ", next-in-ring: " + nextPeerPort + ")");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Node " + nodeId + " shutting down...");
            server.stop(0);
            pool.shutdown();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
    }
}
