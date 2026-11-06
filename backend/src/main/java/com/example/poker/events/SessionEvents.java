package com.example.poker.events;

import com.example.poker.api.Dtos.SessionSnapshot;
import com.example.poker.events.SessionSubscribers.Subscriber;
import com.example.poker.session.Participant;
import com.example.poker.session.SnapshotService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Pushes a full, per-recipient snapshot to every open stream of a session after each change.
 * All sends run on one thread, so a stream never receives an older snapshot after a newer one.
 */
@ApplicationScoped
public class SessionEvents {

    private static final Logger LOG = Logger.getLogger(SessionEvents.class);

    @Inject
    SessionSubscribers subscribers;

    @Inject
    SnapshotService snapshots;

    @ConfigProperty(name = "poker.sse.heartbeat-interval", defaultValue = "15s")
    Duration heartbeatInterval;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-events");
        t.setDaemon(true);
        return t;
    });

    void startHeartbeat(@Observes StartupEvent event) {
        long millis = heartbeatInterval.toMillis();
        executor.scheduleAtFixedRate(this::heartbeat, millis, millis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    public void subscribe(UUID sessionId, UUID participantId, Sse sse, MultiEmitter<? super OutboundSseEvent> emitter) {
        Subscriber subscriber = new Subscriber(participantId, sse, emitter);
        emitter.onTermination(() -> {
            subscribers.remove(sessionId, subscriber);
            broadcast(sessionId);
        });
        subscribers.add(sessionId, subscriber);
        broadcast(sessionId);
    }

    void onChange(@Observes(during = TransactionPhase.AFTER_SUCCESS) SessionChanged event) {
        broadcast(event.sessionId());
    }

    public void broadcast(UUID sessionId) {
        executor.execute(() -> send(sessionId));
    }

    private void send(UUID sessionId) {
        var targets = subscribers.of(sessionId);
        if (targets.isEmpty()) {
            return;
        }
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            QuarkusTransaction.requiringNew().run(() -> targets.forEach(this::sendSnapshot));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Broadcast for session %s failed", sessionId);
        } finally {
            requestContext.terminate();
        }
    }

    private void sendSnapshot(Subscriber subscriber) {
        if (subscriber.emitter().isCancelled()) {
            return;
        }
        Participant participant = Participant.findById(subscriber.participantId());
        if (participant == null) {
            subscriber.emitter().complete();
            return;
        }
        SessionSnapshot snapshot = snapshots.snapshot(participant);
        subscriber.emitter().emit(subscriber.sse().newEventBuilder()
                .name("snapshot")
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(SessionSnapshot.class, snapshot)
                .build());
    }

    private void heartbeat() {
        subscribers.all().values().forEach(subs -> subs.forEach(s -> {
            if (!s.emitter().isCancelled()) {
                s.emitter().emit(s.sse().newEventBuilder().comment("ping").build());
            }
        }));
    }
}
