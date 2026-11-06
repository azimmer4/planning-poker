package com.example.poker.session;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.example.poker.domain.Card;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "estimation_round")
public class EstimationRound extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "issue_iid", nullable = false)
    public int issueIid;

    @Column(nullable = false)
    public int number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RoundStatus status;

    @Column(name = "issue_title")
    public String issueTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_card")
    public Card confirmedCard;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Finished rounds (revealed or confirmed) of a session, oldest first. */
    public static List<EstimationRound> listFinished(UUID sessionId) {
        return list("sessionId = ?1 and status in ?2 order by createdAt", sessionId,
                List.of(RoundStatus.REVEALED, RoundStatus.CONFIRMED));
    }

    public static long countForIssue(UUID sessionId, int issueIid) {
        return count("sessionId = ?1 and issueIid = ?2", sessionId, issueIid);
    }
}
