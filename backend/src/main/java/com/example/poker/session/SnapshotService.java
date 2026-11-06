package com.example.poker.session;

import com.example.poker.api.Dtos.HistoryEntry;
import com.example.poker.api.Dtos.HistoryVote;
import com.example.poker.api.Dtos.IssueView;
import com.example.poker.api.Dtos.ParticipantView;
import com.example.poker.api.Dtos.ResultView;
import com.example.poker.api.Dtos.SessionSnapshot;
import com.example.poker.api.Dtos.VoteView;
import com.example.poker.domain.Card;
import com.example.poker.domain.ResultCalculator;
import com.example.poker.domain.RoundResult;
import com.example.poker.events.SessionSubscribers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the session state as seen by one participant. Never exposes other participants' cards before reveal. */
@ApplicationScoped
public class SnapshotService {

    private static final EnumSet<SessionState> RELEASED =
            EnumSet.of(SessionState.VOTING, SessionState.REVEALED, SessionState.CONFIRMED);
    private static final EnumSet<SessionState> CARDS_OPEN = EnumSet.of(SessionState.REVEALED, SessionState.CONFIRMED);

    @Inject
    ResultCalculator calculator;

    @Inject
    SessionSubscribers subscribers;

    public SessionSnapshot snapshot(Participant viewer) {
        PokerSession session = PokerSession.findById(viewer.sessionId);
        List<Participant> participants = Participant.listBySession(session.id);

        EstimationRound round = session.currentRoundId == null ? null : EstimationRound.findById(session.currentRoundId);
        Map<UUID, Card> votes = round == null ? Map.of() : Vote.listByRound(round.id).stream()
                .filter(v -> v.participantId != null)
                .collect(Collectors.toMap(v -> v.participantId, v -> v.card));

        Function<Participant, ParticipantView> view = p -> new ParticipantView(
                p.id.toString(), p.name, p.role.name(), votes.containsKey(p.id),
                subscribers.isOnline(session.id, p.id));

        return new SessionSnapshot(
                session.id.toString(),
                session.projectId,
                session.state.name(),
                view.apply(viewer),
                participants.stream().map(view).toList(),
                visibleIssue(session, viewer),
                round == null ? null : round.number,
                votes.get(viewer.id),
                CARDS_OPEN.contains(session.state) ? result(participants, votes) : null,
                round == null ? null : round.confirmedCard,
                history(session),
                viewer.role == Role.SCRUM_MASTER ? allVoted(session, participants, votes) : null);
    }

    private static boolean allVoted(PokerSession session, List<Participant> participants, Map<UUID, Card> votes) {
        List<Participant> developers = participants.stream().filter(p -> p.role == Role.DEVELOPER).toList();
        return session.state == SessionState.VOTING
                && !developers.isEmpty()
                && developers.stream().allMatch(p -> votes.containsKey(p.id));
    }

    private static IssueView visibleIssue(PokerSession session, Participant viewer) {
        if (session.selectedIssueIid == null) {
            return null;
        }
        if (viewer.role != Role.SCRUM_MASTER && !RELEASED.contains(session.state)) {
            return null;
        }
        List<String> labels = session.issueLabels == null ? List.of() : List.of(session.issueLabels);
        return new IssueView(session.selectedIssueIid, session.issueTitle, session.issueDescription, session.issueWebUrl, labels);
    }

    private ResultView result(List<Participant> participants, Map<UUID, Card> votes) {
        List<VoteView> voteViews = participants.stream()
                .filter(p -> votes.containsKey(p.id))
                .map(p -> new VoteView(p.id.toString(), p.name, votes.get(p.id)))
                .toList();
        RoundResult result = calculator.calculate(votes.values());
        return new ResultView(voteViews, result.groups(), result.average(), result.mostFrequent());
    }

    private List<HistoryEntry> history(PokerSession session) {
        return EstimationRound.listFinished(session.id).stream()
                .filter(r -> !Objects.equals(r.id, session.currentRoundId))
                .map(this::historyEntry)
                .toList();
    }

    private HistoryEntry historyEntry(EstimationRound round) {
        List<Vote> votes = Vote.listByRound(round.id);
        RoundResult result = calculator.calculate(votes.stream().map(v -> v.card).toList());
        return new HistoryEntry(
                round.issueIid,
                round.issueTitle,
                round.number,
                votes.stream().map(v -> new HistoryVote(v.voterName, v.card)).toList(),
                result.groups(),
                result.average(),
                result.mostFrequent(),
                round.confirmedCard);
    }
}
