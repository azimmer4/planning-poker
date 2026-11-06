package com.example.poker.support;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs the packaged backend ({@code target/quarkus-app}) as a separate JVM in the prod profile. */
public final class BackendProcess implements AutoCloseable {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);
    private static final File JAR = new File("target/quarkus-app/quarkus-run.jar").getAbsoluteFile();

    private final int port;
    private final Map<String, String> env;
    private Process process;
    private int starts;

    public BackendProcess(Map<String, String> env) {
        this.port = freePort();
        this.env = env;
    }

    public int port() {
        return port;
    }

    public void start() {
        if (!JAR.isFile()) {
            throw new IllegalStateException(JAR + " missing; run 'mvn verify' (package phase) first");
        }
        File log = new File("target/e2e-backend-" + (++starts) + ".log");
        ProcessBuilder builder = new ProcessBuilder(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-Dquarkus.http.port=" + port,
                "-jar", JAR.getPath()));
        builder.environment().putAll(env);
        builder.redirectErrorStream(true).redirectOutput(log);
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        awaitReady(log);
    }

    /** Graceful shutdown (SIGTERM), like {@code docker compose restart}. */
    public void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        process = null;
    }

    public void restart() {
        stop();
        start();
    }

    @Override
    public void close() {
        stop();
    }

    private void awaitReady(File log) {
        HttpClient http = HttpClient.newHttpClient();
        URI health = URI.create("http://localhost:" + port + "/q/health/ready");
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Backend exited with " + process.exitValue() + ", see " + log);
            }
            try {
                var response = http.send(HttpRequest.newBuilder(health).build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // not listening yet
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            sleep();
        }
        stop();
        throw new IllegalStateException("Backend not ready within " + STARTUP_TIMEOUT + ", see " + log);
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
