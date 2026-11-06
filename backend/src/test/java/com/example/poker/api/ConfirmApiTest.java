package com.example.poker.api;

import static com.example.poker.support.PokerApi.confirm;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.poker.support.MockGitLab;
import com.example.poker.support.PokerApi.Credentials;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class ConfirmApiTest {

    Credentials sm;
    Credentials tom;
    Credentials lena;

    /** Issue 1 of project 1 starts with labels [frontend, SP::3]. */
    @BeforeEach
    void setUp() {
        MockGitLab.reset();
        sm = createSession("1", 1, "Sarah");
        tom = join(sm.sessionId(), "Tom");
        lena = join(sm.sessionId(), "Lena");
        startRound(sm).statusCode(204);
        vote(tom, "5").statusCode(204);
        vote(lena, "8").statusCode(204);
    }

    @Nested
    class Confirm {

        @Test
        void numericCardReplacesEstimateLabelAndAddsNote() {
            reveal(sm).statusCode(204);

            confirm(sm, "5").statusCode(204);

            assertEquals(List.of("frontend", "SP::5"), MockGitLab.labels("1", 1));
            List<String> notes = MockGitLab.notes("1", 1);
            assertEquals(1, notes.size());
            assertTrue(notes.getFirst().contains("**5**"), notes.getFirst());
        }

        @Test
        void anyDeckCardCanBeChosenNotJustTheMostFrequent() {
            reveal(sm).statusCode(204);

            confirm(sm, "13").statusCode(204);

            assertEquals(List.of("frontend", "SP::13"), MockGitLab.labels("1", 1));
        }

        @ParameterizedTest
        @ValueSource(strings = {"?", "☕"})
        void specialCardOnlyAddsNoteAndKeepsLabels(String card) {
            reveal(sm).statusCode(204);

            confirm(sm, card).statusCode(204);

            assertEquals(List.of("frontend", "SP::3"), MockGitLab.labels("1", 1));
            assertTrue(MockGitLab.notes("1", 1).getFirst().contains(card));
        }

        @ParameterizedTest
        @ValueSource(strings = {"6.5", "4", "", "SP::5"})
        void valuesOutsideTheDeckAre400(String card) {
            reveal(sm).statusCode(204);

            confirm(sm, card).statusCode(400);

            assertEquals(List.of("frontend", "SP::3"), MockGitLab.labels("1", 1));
        }

        @Test
        void snapshotShowsConfirmedStateAndKeepsResult() {
            reveal(sm).statusCode(204);
            confirm(sm, "5").statusCode(204);

            JsonPath s = snapshot(tom);
            assertEquals("CONFIRMED", s.getString("state"));
            assertEquals("5", s.getString("confirmedCard"));
            assertEquals(List.of("5", "8"), s.getList("result.votes.card"));
            assertEquals(1, s.getInt("issue.iid"));
        }

        @Test
        void beforeRevealIs409() {
            confirm(sm, "5").statusCode(409);
            assertTrue(MockGitLab.notes("1", 1).isEmpty());
        }

        @Test
        void confirmingTwiceIs409() {
            reveal(sm).statusCode(204);
            confirm(sm, "5").statusCode(204);

            confirm(sm, "8").statusCode(409);
            assertEquals(1, MockGitLab.notes("1", 1).size());
        }

        @Test
        void restartAfterConfirmIs409() {
            reveal(sm).statusCode(204);
            confirm(sm, "5").statusCode(204);

            restart(sm).statusCode(409);
        }

        @Test
        void developerCannotConfirm() {
            reveal(sm).statusCode(204);

            confirm(tom, "5").statusCode(403);
        }

        @Test
        void gitLabFailureIs502AndRoundCanBeConfirmedAgain() {
            reveal(sm).statusCode(204);
            MockGitLab.failNext();

            confirm(sm, "5").statusCode(502);
            assertEquals("REVEALED", snapshot(sm).getString("state"));
            assertEquals(List.of("frontend", "SP::3"), MockGitLab.labels("1", 1));

            confirm(sm, "5").statusCode(204);
            assertEquals(List.of("frontend", "SP::5"), MockGitLab.labels("1", 1));
            assertEquals(1, MockGitLab.notes("1", 1).size());
        }

        @Test
        void nextIssueCanBeSelectedAfterConfirm() {
            reveal(sm).statusCode(204);
            confirm(sm, "5").statusCode(204);

            selectIssue(sm, 2).statusCode(204);
            startRound(sm).statusCode(204);

            JsonPath s = snapshot(sm);
            assertEquals("VOTING", s.getString("state"));
            assertEquals(1, s.getInt("roundNumber"));
            assertNull(s.get("confirmedCard"));
        }
    }

    @Nested
    class History {

        @Test
        void isEmptyAtTheStart() {
            assertEquals(List.of(), snapshot(sm).getList("history"));
        }

        @Test
        void restartMovesRevealedRoundIntoHistory() {
            reveal(sm).statusCode(204);
            restart(sm).statusCode(204);

            JsonPath s = snapshot(tom);
            assertEquals(1, s.getList("history").size());
            assertEquals(1, s.getInt("history[0].issueIid"));
            assertEquals("Login-Seite überarbeiten", s.getString("history[0].issueTitle"));
            assertEquals(1, s.getInt("history[0].roundNumber"));
            assertEquals(List.of("Tom", "Lena"), s.getList("history[0].votes.name"));
            assertEquals(List.of("5", "8"), s.getList("history[0].votes.card"));
            assertEquals(6.5, s.getDouble("history[0].average"), 0.0001);
            assertNull(s.get("history[0].confirmedCard"));
        }

        @Test
        void confirmedRoundAppearsWithItsValueOnceTheNextIssueIsSelected() {
            reveal(sm).statusCode(204);
            restart(sm).statusCode(204);
            vote(tom, "8").statusCode(204);
            reveal(sm).statusCode(204);
            confirm(sm, "8").statusCode(204);

            selectIssue(sm, 2).statusCode(204);

            JsonPath s = snapshot(sm);
            assertEquals(List.of(1, 2), s.getList("history.roundNumber"));
            assertEquals("8", s.getString("history[1].confirmedCard"));
        }

        @Test
        void discardedRoundIsNotRecorded() {
            selectIssue(sm, 2).statusCode(204);

            assertEquals(List.of(), snapshot(sm).getList("history"));
        }

        @Test
        void votesOfParticipantsWhoLeftStayInTheHistory() {
            reveal(sm).statusCode(204);
            restart(sm).statusCode(204);

            leave(tom).statusCode(204);

            assertEquals(List.of("Tom", "Lena"), snapshot(sm).getList("history[0].votes.name"));
        }
    }
}
