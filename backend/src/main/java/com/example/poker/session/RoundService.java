package com.example.poker.session;

import com.example.poker.api.ApiException;
import com.example.poker.domain.Card;
import com.example.poker.domain.ResultCalculator;
import com.example.poker.domain.RoundResult;
import com.example.poker.events.SessionChanged;
import com.example.poker.gitlab.GitLabIssue;
import com.example.poker.gitlab.GitLabService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** State machine of an estimation round. All mutations lock the session row to serialize concurrent actions. */
@ApplicationScoped
public class RoundService {

    @Inject
    Event<SessionChanged> changed;

    @Inject
    GitLabService gitLab;

    @Inject
    ResultCalculator calculator;

    @Transactional
    public void selectIssue(Participant actor, int issueIid) {
        PokerSession unlocked = PokerSession.findById(actor.sessionId);
        GitLabIssue issue = gitLab.getIssue(unlocked.projectId, issueIid);

        PokerSession session = lock(actor.sessionId);
        if (session.state == SessionState.VOTING) {
            discardCurrentRound(session);
        }
        session.currentRoundId = null;
        session.selectIssue(issue);
        session.state = SessionState.ISSUE_SELECTED;
    }

    @Transactional
    public void startRound(Participant actor) {
        PokerSession session = lock(actor.sessionId);
        requireState(session, SessionState.ISSUE_SELECTED, "No issue selected or round already running");
        openRound(session);
    }

    @Transactional
    public void restart(Participant actor) {
        PokerSession session = lock(actor.sessionId);
        requireState(session, SessionState.REVEALED, "Round can only be restarted after reveal");
        openRound(session);
    }

    @Transactional
    public void vote(Participant voter, Card card) {
        PokerSession session = lock(voter.sessionId);
        requireState(session, SessionState.VOTING, "No round open for voting");

        Vote vote = Vote.find(session.currentRoundId, voter.id).orElseGet(() -> {
            Vote v = new Vote();
            v.id = UUID.randomUUID();
            v.roundId = session.currentRoundId;
            v.participantId = voter.id;
            v.voterName = voter.name;
            v.votedAt = Instant.now();
            return v;
        });
        vote.card = card;
        vote.persist();
    }

    @Transactional
    public void reveal(Participant actor) {
        PokerSession session = lock(actor.sessionId);
        requireState(session, SessionState.VOTING, "No round open for voting");

        EstimationRound round = EstimationRound.findById(session.currentRoundId);
        round.status = RoundStatus.REVEALED;
        session.state = SessionState.REVEALED;
    }

    /**
     * Writes the agreed card to GitLab and closes the round. If GitLab fails the transaction rolls back,
     * the round stays revealed and the scrum master can retry.
     */
    @Transactional
    public void confirm(Participant actor, Card card) {
        PokerSession session = lock(actor.sessionId);
        requireState(session, SessionState.REVEALED, "Only a revealed round can be confirmed");

        EstimationRound round = EstimationRound.findById(session.currentRoundId);
        var labels = gitLab.writeEstimate(session.projectId, round.issueIid, card, note(round, card));

        round.status = RoundStatus.CONFIRMED;
        round.confirmedCard = card;
        session.issueLabels = labels.toArray(String[]::new);
        session.state = SessionState.CONFIRMED;
    }

    @Transactional
    public void leave(Participant participant) {
        PokerSession session = lock(participant.sessionId);
        if (participant.role == Role.SCRUM_MASTER) {
            throw ApiException.conflict("Scrum master cannot leave the session");
        }
        if (session.currentRoundId != null && session.state != SessionState.CONFIRMED) {
            Vote.delete("roundId = ?1 and participantId = ?2", session.currentRoundId, participant.id);
        }
        Participant.deleteById(participant.id);
    }

    private String note(EstimationRound round, Card card) {
        RoundResult result = calculator.calculate(Vote.listByRound(round.id).stream().map(v -> v.card).toList());
        String headline = card.isNumeric()
                ? "Planning Poker: vereinbarte Schätzung **%s** Story Points (Runde %d).".formatted(card.value(), round.number)
                : "Planning Poker: Ergebnis **%s**, keine Story Points vergeben (Runde %d).".formatted(card.value(), round.number);
        String votes = result.groups().isEmpty() ? "keine"
                : result.groups().stream().map(g -> g.card().value() + " ×" + g.count()).collect(Collectors.joining(", "));
        String average = result.average() == null ? "–" : String.format(Locale.ROOT, "%.1f", result.average());
        return headline + "\n\nStimmen: " + votes + " · Durchschnitt: " + average;
    }

    private void openRound(PokerSession session) {
        EstimationRound round = new EstimationRound();
        round.id = UUID.randomUUID();
        round.sessionId = session.id;
        round.issueIid = session.selectedIssueIid;
        round.issueTitle = session.issueTitle;
        round.number = (int) EstimationRound.countForIssue(session.id, session.selectedIssueIid) + 1;
        round.status = RoundStatus.VOTING;
        round.createdAt = Instant.now();
        round.persist();

        session.currentRoundId = round.id;
        session.state = SessionState.VOTING;
    }

    private void discardCurrentRound(PokerSession session) {
        UUID roundId = session.currentRoundId;
        session.currentRoundId = null;
        PokerSession.getEntityManager().flush();
        Vote.delete("roundId", roundId);
        EstimationRound.deleteById(roundId);
    }

    /** Locks the session row and announces a change, which is broadcast only if the transaction commits. */
    private PokerSession lock(UUID sessionId) {
        PokerSession session = PokerSession.findById(sessionId, LockModeType.PESSIMISTIC_WRITE);
        if (session == null) {
            throw ApiException.notFound("Session not found");
        }
        changed.fire(new SessionChanged(sessionId));
        return session;
    }

    private static void requireState(PokerSession session, SessionState expected, String message) {
        if (session.state != expected) {
            throw ApiException.conflict(message);
        }
    }
}
