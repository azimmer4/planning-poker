package com.example.poker.api;

import static com.example.poker.support.PokerApi.as;
import static com.example.poker.support.PokerApi.createSession;
import static com.example.poker.support.PokerApi.join;
import static com.example.poker.support.PokerApi.leave;
import static com.example.poker.support.PokerApi.restart;
import static com.example.poker.support.PokerApi.reveal;
import static com.example.poker.support.PokerApi.selectIssue;
import static com.example.poker.support.PokerApi.snapshot;
import static com.example.poker.support.PokerApi.startRound;
import static com.example.poker.support.PokerApi.vote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.poker.support.PokerApi.Credentials;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class RoundFlowApiTest {

    Credentials sm;
    Credentials tom;
    Credentials lena;

    @BeforeEach
    void setUp() {
        sm = createSession("1", null, "Sarah");
        tom = join(sm.sessionId(), "Tom");
        lena = join(sm.sessionId(), "Lena");
    }

    private void startVotingOn(int issueIid) {
        selectIssue(sm, issueIid).statusCode(204);
        startRound(sm).statusCode(204);
    }

    private static Map<String, Object> participant(JsonPath snapshot, String name) {
        return snapshot.getMap("participants.find { it.name == '" + name + "' }");
    }

    @Nested
    class SelectIssue {

        @Test
        void scrumMasterSelectsIssue() {
            selectIssue(sm, 5).statusCode(204);

            var s = snapshot(sm);
            assertEquals("ISSUE_SELECTED", s.getString("state"));
            assertEquals(5, s.getInt("issue.iid"));
        }

        @Test
        void developerDoesNotSeeIssueBeforeRelease() {
            selectIssue(sm, 5).statusCode(204);

            assertNull(snapshot(tom).get("issue"));
        }

        @Test
        void developerCannotSelectIssue() {
            selectIssue(tom, 5).statusCode(403);
        }

        @Test
        void invalidIssueIidIs400() {
            selectIssue(sm, 0).statusCode(400);
            selectIssue(sm, null).statusCode(400);
        }
    }

    @Nested
    class StartRound {

        @Test
        void withoutSelectedIssueIs409() {
            startRound(sm).statusCode(409);
        }

        @Test
        void releasesIssueToEveryone() {
            startVotingOn(5);

            var s = snapshot(tom);
            assertEquals("VOTING", s.getString("state"));
            assertEquals(5, s.getInt("issue.iid"));
            assertEquals(1, s.getInt("roundNumber"));
        }

        @Test
        void issueGivenOnCreationCanBeReleasedDirectly() {
            var other = createSession("1", 4, "Max");
            startRound(other).statusCode(204);

            assertEquals(4, snapshot(other).getInt("issue.iid"));
        }

        @Test
        void developerCannotStart() {
            selectIssue(sm, 5).statusCode(204);

            startRound(tom).statusCode(403);
        }

        @Test
        void whileVotingIs409() {
            startVotingOn(5);

            startRound(sm).statusCode(409);
        }
    }

    @Nested
    class Voting {

        @Test
        void beforeReleaseIs409() {
            selectIssue(sm, 5).statusCode(204);

            vote(tom, "5").statusCode(409);
        }

        @Test
        void progressIsVisibleWithoutRevealingValues() {
            startVotingOn(5);

            vote(tom, "8").statusCode(204);

            var seenByLena = snapshot(lena);
            assertEquals(true, participant(seenByLena, "Tom").get("hasVoted"));
            assertEquals(false, participant(seenByLena, "Lena").get("hasVoted"));
            assertNull(seenByLena.get("myVote"));
            assertNull(seenByLena.get("result"));
            assertFalse(seenByLena.prettify().contains("\"8\""), "vote value must not leak");

            assertEquals("8", snapshot(tom).getString("myVote"));
        }

        @Test
        void voteCanBeChangedUntilReveal() {
            startVotingOn(5);

            vote(tom, "3").statusCode(204);
            vote(tom, "13").statusCode(204);
            reveal(sm).statusCode(204);

            var votes = snapshot(sm).getList("result.votes.card");
            assertEquals(List.of("13"), votes);
        }

        @ParameterizedTest
        @ValueSource(strings = {"?", "☕", "0", "34"})
        void acceptsEveryCardOfTheDeck(String card) {
            startVotingOn(5);

            vote(tom, card).statusCode(204);

            assertEquals(card, snapshot(tom).getString("myVote"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"4", "100", "", "2.75"})
        void rejectsCardsOutsideTheDeck(String card) {
            startVotingOn(5);

            vote(tom, card).statusCode(400);
        }

        @Test
        void missingCardIs400() {
            startVotingOn(5);

            vote(tom, null).statusCode(400);
        }

        @Test
        void scrumMasterCannotVote() {
            startVotingOn(5);

            vote(sm, "5").statusCode(403);
        }

        @Test
        void lateJoinerCountsAndCanVote() {
            startVotingOn(5);

            var max = join(sm.sessionId(), "Max");
            assertEquals(false, participant(snapshot(sm), "Max").get("hasVoted"));

            vote(max, "5").statusCode(204);
            assertEquals(true, participant(snapshot(sm), "Max").get("hasVoted"));
        }
    }

    @Nested
    class Reveal {

        @Test
        void isPossibleBeforeEveryoneVotedAndShowsResult() {
            startVotingOn(5);
            vote(tom, "5").statusCode(204);

            reveal(sm).statusCode(204);

            var s = snapshot(lena);
            assertEquals("REVEALED", s.getString("state"));
            assertEquals(List.of("Tom"), s.getList("result.votes.name"));
            assertEquals(List.of("5"), s.getList("result.votes.card"));
            assertEquals(tom.participantId(), s.getString("result.votes[0].participantId"));
        }

        @Test
        void resultContainsGroupsAverageAndMostFrequent() {
            startVotingOn(5);
            vote(tom, "3").statusCode(204);
            vote(lena, "8").statusCode(204);
            var max = join(sm.sessionId(), "Max");
            vote(max, "8").statusCode(204);

            reveal(sm).statusCode(204);

            var s = snapshot(tom);
            assertEquals(List.of("Tom", "Lena", "Max"), s.getList("result.votes.name"));
            assertEquals(List.of("3", "8"), s.getList("result.groups.card"));
            assertEquals(List.of(1, 2), s.getList("result.groups.count"));
            assertEquals(19.0 / 3, s.getDouble("result.average"), 0.0001);
            assertEquals("8", s.getString("result.mostFrequent"));
        }

        @Test
        void withoutRunningRoundIs409() {
            selectIssue(sm, 5).statusCode(204);

            reveal(sm).statusCode(409);
        }

        @Test
        void developerCannotReveal() {
            startVotingOn(5);

            reveal(tom).statusCode(403);
        }

        @Test
        void votingAfterRevealIs409() {
            startVotingOn(5);
            reveal(sm).statusCode(204);

            vote(tom, "5").statusCode(409);
        }
    }

    @Nested
    class Restart {

        @Test
        void startsNewRoundForSameIssueAndResetsVotes() {
            startVotingOn(5);
            vote(tom, "5").statusCode(204);
            reveal(sm).statusCode(204);

            restart(sm).statusCode(204);

            var s = snapshot(tom);
            assertEquals("VOTING", s.getString("state"));
            assertEquals(5, s.getInt("issue.iid"));
            assertEquals(2, s.getInt("roundNumber"));
            assertNull(s.get("myVote"));
            assertNull(s.get("result"));
            assertEquals(false, participant(s, "Tom").get("hasVoted"));
        }

        @Test
        void beforeRevealIs409() {
            startVotingOn(5);

            restart(sm).statusCode(409);
        }

        @Test
        void developerCannotRestart() {
            startVotingOn(5);
            reveal(sm).statusCode(204);

            restart(tom).statusCode(403);
        }
    }

    @Nested
    class SwitchIssue {

        @Test
        void duringVotingDiscardsVotesAndRequiresNewRelease() {
            startVotingOn(5);
            vote(tom, "5").statusCode(204);

            selectIssue(sm, 6).statusCode(204);

            var s = snapshot(sm);
            assertEquals("ISSUE_SELECTED", s.getString("state"));
            assertEquals(6, s.getInt("issue.iid"));
            assertNull(snapshot(tom).get("issue"));
            vote(tom, "5").statusCode(409);

            startRound(sm).statusCode(204);
            var afterRelease = snapshot(tom);
            assertEquals(1, afterRelease.getInt("roundNumber"));
            assertNull(afterRelease.get("myVote"));
            assertEquals(false, participant(afterRelease, "Tom").get("hasVoted"));
        }

        @Test
        void afterRevealIsAllowed() {
            startVotingOn(5);
            reveal(sm).statusCode(204);

            selectIssue(sm, 6).statusCode(204);

            assertEquals("ISSUE_SELECTED", snapshot(sm).getString("state"));
        }
    }

    @Nested
    class Leave {

        @Test
        void developerIsRemovedAndTokenInvalidated() {
            leave(tom).statusCode(204);

            assertEquals(List.of("Sarah", "Lena"), snapshot(sm).getList("participants.name"));
            as(tom).get("/api/sessions/{id}", sm.sessionId()).then().statusCode(401);
        }

        @Test
        void removesVoteOfCurrentRound() {
            startVotingOn(5);
            vote(tom, "5").statusCode(204);
            vote(lena, "8").statusCode(204);

            leave(tom).statusCode(204);
            reveal(sm).statusCode(204);

            assertEquals(List.of("Lena"), snapshot(sm).getList("result.votes.name"));
        }

        @Test
        void nameCanBeReusedAfterLeaving() {
            leave(tom).statusCode(204);

            assertTrue(join(sm.sessionId(), "Tom").token() != null);
        }

        @Test
        void scrumMasterCannotLeave() {
            leave(sm).statusCode(409);
        }
    }
}
