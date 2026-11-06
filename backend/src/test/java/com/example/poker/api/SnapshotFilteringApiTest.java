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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The snapshot is the only read model: it must never leak hidden information to the wrong viewer. */
@QuarkusTest
class SnapshotFilteringApiTest {

    Credentials sm;
    Credentials tom;
    Credentials lena;

    @BeforeEach
    void setUp() {
        sm = createSession("1", null, "Sarah");
        tom = join(sm.sessionId(), "Tom");
        lena = join(sm.sessionId(), "Lena");
        selectIssue(sm, 2).statusCode(204);
        startRound(sm).statusCode(204);
    }

    private static String rawSnapshot(Credentials c) {
        return as(c).get("/api/sessions/{id}", c.sessionId()).then().statusCode(200).extract().asString();
    }

    @Nested
    class HiddenCards {

        @Test
        void foreignCardAppearsNowhereInOtherSnapshotsBeforeReveal() {
            vote(tom, "21").statusCode(204);

            assertFalse(rawSnapshot(lena).contains("\"21\""), "developer must not see Tom's card");
            assertFalse(rawSnapshot(sm).contains("\"21\""), "scrum master must not see Tom's card");
            assertTrue(rawSnapshot(tom).contains("\"21\""), "Tom sees his own card");
        }

        @Test
        void specialCardsAreHiddenToo() {
            vote(tom, "☕").statusCode(204);

            assertFalse(rawSnapshot(lena).contains("☕"));
            assertFalse(rawSnapshot(sm).contains("☕"));
        }

        @Test
        void cardsBecomeVisibleToEveryoneAfterReveal() {
            vote(tom, "21").statusCode(204);
            reveal(sm).statusCode(204);

            assertTrue(rawSnapshot(lena).contains("\"21\""));
            assertTrue(rawSnapshot(sm).contains("\"21\""));
        }

        @Test
        void previousResultMovesToHistoryAfterRestart() {
            vote(tom, "21").statusCode(204);
            reveal(sm).statusCode(204);
            restart(sm).statusCode(204);

            var s = snapshot(tom);
            assertNull(s.get("result"));
            assertNull(s.get("myVote"));
            assertEquals("21", s.getString("history[0].votes[0].card"));
        }

        @Test
        void previousResultMovesToHistoryAfterIssueSwitch() {
            vote(tom, "21").statusCode(204);
            reveal(sm).statusCode(204);
            selectIssue(sm, 3).statusCode(204);

            var s = snapshot(sm);
            assertNull(s.get("result"));
            assertEquals("21", s.getString("history[0].votes[0].card"));
        }

        @Test
        void unrevealedRoundNeverReachesTheHistory() {
            vote(tom, "21").statusCode(204);
            selectIssue(sm, 3).statusCode(204);

            assertFalse(rawSnapshot(lena).contains("\"21\""));
            assertFalse(rawSnapshot(sm).contains("\"21\""));
        }
    }

    @Nested
    class AllVoted {

        @Test
        void isFalseForScrumMasterWhileVotesAreMissing() {
            vote(tom, "5").statusCode(204);

            assertEquals(false, snapshot(sm).get("allVoted"));
        }

        @Test
        void isTrueForScrumMasterOnceEveryDeveloperVoted() {
            vote(tom, "5").statusCode(204);
            vote(lena, "8").statusCode(204);

            assertEquals(true, snapshot(sm).get("allVoted"));
        }

        @Test
        void isNeverSentToDevelopers() {
            vote(tom, "5").statusCode(204);
            vote(lena, "8").statusCode(204);

            assertNull(snapshot(tom).get("allVoted"));
            assertFalse(rawSnapshot(tom).contains("allVoted"));
        }

        @Test
        void lateJoinerMakesItFalseAgain() {
            vote(tom, "5").statusCode(204);
            vote(lena, "8").statusCode(204);
            join(sm.sessionId(), "Max");

            assertEquals(false, snapshot(sm).get("allVoted"));
        }

        @Test
        void becomesTrueWhenLastMissingDeveloperLeaves() {
            vote(tom, "5").statusCode(204);
            leave(lena).statusCode(204);

            assertEquals(true, snapshot(sm).get("allVoted"));
        }

        @Test
        void isFalseWithoutAnyDeveloper() {
            Credentials lonely = createSession("1", 2, "Solo");
            startRound(lonely).statusCode(204);

            assertEquals(false, snapshot(lonely).get("allVoted"));
        }

        @Test
        void isFalseOutsideVoting() {
            vote(tom, "5").statusCode(204);
            vote(lena, "8").statusCode(204);
            reveal(sm).statusCode(204);

            assertEquals(false, snapshot(sm).get("allVoted"));
        }
    }
}
