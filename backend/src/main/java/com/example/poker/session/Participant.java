package com.example.poker.session;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "participant")
public class Participant extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    @Column(name = "token_hash", nullable = false, unique = true)
    public String tokenHash;

    @Column(name = "joined_at", nullable = false)
    public Instant joinedAt;

    public static List<Participant> listBySession(UUID sessionId) {
        return list("sessionId = ?1 order by joinedAt", sessionId);
    }

    public static Optional<Participant> findBySessionAndTokenHash(UUID sessionId, String tokenHash) {
        return find("sessionId = ?1 and tokenHash = ?2", sessionId, tokenHash).firstResultOptional();
    }

    public static boolean nameTaken(UUID sessionId, String name) {
        return count("sessionId = ?1 and lower(name) = lower(?2)", sessionId, name) > 0;
    }
}
