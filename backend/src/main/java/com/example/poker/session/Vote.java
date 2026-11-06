package com.example.poker.session;

import com.example.poker.domain.Card;
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
@Table(name = "vote")
public class Vote extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "round_id", nullable = false)
    public UUID roundId;

    /** {@code null} once the voter left the session. */
    @Column(name = "participant_id")
    public UUID participantId;

    @Column(name = "voter_name", nullable = false)
    public String voterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Card card;

    /** Time of the first vote in the round; not changed when the card is changed. */
    @Column(name = "voted_at", nullable = false)
    public Instant votedAt;

    public static List<Vote> listByRound(UUID roundId) {
        return list("roundId = ?1 order by votedAt", roundId);
    }

    public static Optional<Vote> find(UUID roundId, UUID participantId) {
        return find("roundId = ?1 and participantId = ?2", roundId, participantId).firstResultOptional();
    }
}
