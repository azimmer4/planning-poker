package com.example.poker.support;

import static org.junit.jupiter.api.Assertions.fail;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Minimal Server-Sent-Events client for tests; collects {@code snapshot} events and heartbeat comments. */
public final class SseClient implements AutoCloseable {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final BlockingQueue<JsonPath> snapshots = new LinkedBlockingQueue<>();
    private final List<JsonPath> seen = new ArrayList<>();
    private final AtomicInteger heartbeats = new AtomicInteger();
    private final CompletableFuture<Void> ended = new CompletableFuture<>();
    private Thread reader;

    private SseClient() {
    }

    public static SseClient connect(PokerApi.Credentials c) {
        return connect(c.sessionId(), c.token());
    }

    public static SseClient connect(String sessionId, String token) {
        SseClient client = new SseClient();
        client.open(sessionId, token);
        return client;
    }

    private void open(String sessionId, String token) {
        URI uri = URI.create("http://localhost:" + RestAssured.port + "/api/sessions/" + sessionId + "/events?token=" + token);
        HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET().build();
        HttpResponse<Stream<String>> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("SSE connect failed with " + response.statusCode());
        }
        reader = Thread.ofVirtual().start(() -> read(response.body()));
    }

    private void read(Stream<String> lines) {
        String event = null;
        StringBuilder data = new StringBuilder();
        try (lines) {
            for (var it = lines.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.isEmpty()) {
                    if ("snapshot".equals(event) && !data.isEmpty()) {
                        snapshots.add(new JsonPath(data.toString()));
                    }
                    event = null;
                    data.setLength(0);
                } else if (line.startsWith(":")) {
                    heartbeats.incrementAndGet();
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
            }
        } catch (RuntimeException ignored) {
            // connection closed by close()
        } finally {
            ended.complete(null);
        }
    }

    /** Waits for the next snapshot event (in order) that matches; earlier non-matching events are skipped. */
    public JsonPath awaitSnapshot(Predicate<JsonPath> condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                JsonPath next = snapshots.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (next == null) {
                    break;
                }
                seen.add(next);
                if (condition.test(next)) {
                    return next;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return fail("No matching snapshot within " + TIMEOUT + "; last seen: "
                + (seen.isEmpty() ? "none" : seen.getLast().prettify()));
    }

    public JsonPath awaitFirstSnapshot() {
        return awaitSnapshot(s -> true);
    }

    public void assertNoSnapshotWithin(Duration duration) {
        try {
            JsonPath next = snapshots.poll(duration.toMillis(), TimeUnit.MILLISECONDS);
            if (next != null) {
                fail("Unexpected snapshot: " + next.prettify());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void awaitHeartbeat() {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (heartbeats.get() == 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
            sleep(50);
        }
        if (heartbeats.get() == 0) {
            fail("No heartbeat within " + TIMEOUT);
        }
    }

    public void awaitClosedByServer() {
        try {
            ended.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail("Stream was not closed by the server within " + TIMEOUT);
        }
    }

    @Override
    public void close() {
        http.shutdownNow();
        if (reader != null) {
            reader.interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
