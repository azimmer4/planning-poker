package com.example.poker.events;

import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Open SSE streams per session. A participant is online while at least one of his streams is open. */
@ApplicationScoped
public class SessionSubscribers {

    public record Subscriber(UUID participantId, Sse sse, MultiEmitter<? super OutboundSseEvent> emitter) {
    }

    private final Map<UUID, Set<Subscriber>> bySession = new ConcurrentHashMap<>();

    void add(UUID sessionId, Subscriber subscriber) {
        bySession.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(subscriber);
    }

    void remove(UUID sessionId, Subscriber subscriber) {
        bySession.computeIfPresent(sessionId, (k, subs) -> {
            subs.remove(subscriber);
            return subs.isEmpty() ? null : subs;
        });
    }

    Set<Subscriber> of(UUID sessionId) {
        return bySession.getOrDefault(sessionId, Set.of());
    }

    Map<UUID, Set<Subscriber>> all() {
        return bySession;
    }

    public boolean isOnline(UUID sessionId, UUID participantId) {
        return of(sessionId).stream().anyMatch(s -> s.participantId().equals(participantId));
    }
}
