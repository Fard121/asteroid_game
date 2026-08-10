package dk.sdu.mmmi.cbse.main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The smallest thing that lets an external shell talk to the running game:
 * a loopback-only {@link ServerSocket} that reads one command line per
 * connection and writes back whatever the game made of it.
 *
 * <p><b>It never touches game state itself.</b> The accept thread only
 * parks a {@link PendingCommand} on a queue; {@code Game} drains that queue
 * at the top of a frame, on the JavaFX application thread, and completes
 * the command's future with the result. That handoff - not a lock - is what
 * keeps plugin loading/unloading free of races with {@code update()}:
 *
 * <pre>
 *   shell -&gt; socket thread -&gt; queue -&gt; game thread (frame start) -&gt; response
 * </pre>
 *
 * <p>Binding is deliberately restricted to the loopback address, so this
 * opens nothing to the network. If the port is unavailable the game simply
 * runs without the command channel rather than failing to start.
 */
final class PluginCommandServer implements AutoCloseable {

    /** Overridable with {@code -Dasteroids.plugin.port=...}. */
    static final int DEFAULT_PORT =
            Integer.getInteger("asteroids.plugin.port", 5599);

    /**
     * How long a shell client waits for the game loop to pick its command
     * up. Generous compared to a 60 FPS frame; only reached if the loop is
     * genuinely not running.
     */
    private static final long RESPONSE_TIMEOUT_SECONDS = 5;

    /** One command, in flight between the socket thread and the game loop. */
    static final class PendingCommand {

        final String commandLine;
        private final CompletableFuture<String> response = new CompletableFuture<>();

        PendingCommand(String commandLine) {
            this.commandLine = commandLine;
        }

        /** Called on the game thread once the command has been executed. */
        void complete(String result) {
            response.complete(result);
        }

        String awaitResponse() throws InterruptedException, ExecutionException, TimeoutException {
            return response.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private final ServerSocket serverSocket;
    private final Queue<PendingCommand> queue;
    private final Thread acceptThread;
    private volatile boolean closed;

    private PluginCommandServer(ServerSocket serverSocket, Queue<PendingCommand> queue) {
        this.serverSocket = serverSocket;
        this.queue = queue;
        this.acceptThread = new Thread(this::acceptLoop, "plugin-command-server");
        // Daemon: the command channel must never be the reason the JVM
        // stays alive after the game window closes.
        this.acceptThread.setDaemon(true);
    }

    /**
     * Starts the listener, or returns {@code null} (after logging) if the
     * port cannot be bound - a missing command channel is never a reason to
     * fail the game.
     */
    static PluginCommandServer start(int port, Queue<PendingCommand> queue) {
        try {
            ServerSocket socket = new ServerSocket(port, 16, InetAddress.getLoopbackAddress());
            PluginCommandServer server = new PluginCommandServer(socket, queue);
            server.acceptThread.start();
            System.out.println("[plugin] command channel listening on 127.0.0.1:" + port
                    + " - try: game plugin list");
            return server;
        } catch (IOException e) {
            System.err.println("[plugin] could not open the command channel on port " + port
                    + " (" + e.getMessage() + ") - the game runs normally, "
                    + "but 'game plugin ...' will not reach it.");
            return null;
        }
    }

    private void acceptLoop() {
        while (!closed) {
            try (Socket socket = serverSocket.accept()) {
                handleConnection(socket);
            } catch (IOException e) {
                if (!closed) {
                    System.err.println("[plugin] command connection failed: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(
                new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        String commandLine = in.readLine();
        if (commandLine == null || commandLine.isBlank()) {
            out.println("empty command - try: plugin list");
            return;
        }

        PendingCommand command = new PendingCommand(commandLine.trim());
        queue.add(command);
        try {
            out.print(command.awaitResponse());
            out.flush();
        } catch (TimeoutException e) {
            out.println("timed out waiting for the game loop - is the game still running?");
        } catch (ExecutionException e) {
            out.println("command failed: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.println("interrupted");
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Shutting down anyway.
        }
    }
}
