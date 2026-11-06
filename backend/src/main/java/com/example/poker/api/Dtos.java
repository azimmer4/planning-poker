package com.example.poker.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.poker.domain.Card;
import com.example.poker.domain.CardCount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record CreateSessionRequest(
            @NotBlank @Size(max = 255) String projectId,
            @Positive Integer issueIid,
            @NotBlank @Size(max = 40) String name) {
    }

    public record JoinSessionRequest(@NotBlank @Size(max = 40) String name) {
    }

    public record SelectIssueRequest(@NotNull @Positive Integer issueIid) {
    }

    public record VoteRequest(@NotNull Card card) {
    }

    public record ConfirmRequest(@NotNull Card card) {
    }

    public record IssueView(int iid, String title, String description, String webUrl, List<String> labels) {
    }

    public record IssueListView(List<IssueView> issues, Integer nextPage) {
    }

    /** Returned once on create/join; the token is never shown again. */
    public record Credentials(String sessionId, String participantId, String token) {
    }

    public record ParticipantView(String id, String name, String role, boolean hasVoted, boolean online) {
    }

    public record VoteView(String participantId, String name, Card card) {
    }

    public record ResultView(List<VoteView> votes, List<CardCount> groups, Double average, Card mostFrequent) {
    }

    public record HistoryVote(String name, Card card) {
    }

    /** A finished (revealed or confirmed) round of the session protocol. */
    public record HistoryEntry(
            int issueIid,
            String issueTitle,
            int roundNumber,
            List<HistoryVote> votes,
            List<CardCount> groups,
            Double average,
            Card mostFrequent,
            Card confirmedCard) {
    }

    /**
     * Session state as seen by one participant.
     *
     * @param issue    for developers only set once the issue is released
     * @param myVote   the viewer's own card in the current round
     * @param result   only set after reveal
     * @param confirmedCard the card written to GitLab, once confirmed
     * @param history  finished earlier rounds, oldest first
     * @param allVoted only sent to the scrum master: every developer has voted in the running round
     */
    public record SessionSnapshot(
            String sessionId,
            String projectId,
            String state,
            ParticipantView me,
            List<ParticipantView> participants,
            IssueView issue,
            Integer roundNumber,
            Card myVote,
            ResultView result,
            Card confirmedCard,
            List<HistoryEntry> history,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean allVoted) {
    }
}
