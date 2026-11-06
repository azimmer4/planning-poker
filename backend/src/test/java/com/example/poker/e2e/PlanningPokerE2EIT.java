package com.example.poker.e2e;

import static com.example.poker.support.PokerApi.confirm;
import static com.example.poker.support.PokerApi.createSession;
import static com.example.poker.support.PokerApi.issues;
import static com.example.poker.support.PokerApi.join;
import static com.example.poker.support.PokerApi.reveal;
import static com.example.poker.support.PokerApi.restart;
import static com.example.poker.support.PokerApi.selectIssue;
import static com.example.poker.support.PokerApi.snapshot;
import static com.example.poker.support.PokerApi.startRound;
import static com.example.poker.support.PokerApi.vote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.poker.support.BackendProcess;
import com.example.poker.support.GitLabMockResource;
import com.example.poker.support.MockGitLab;
import com.example.poker.support.PokerApi.Credentials;
import com.example.poker.support.SseClient;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Black-box end-to-end test: the packaged backend runs as its own JVM (prod profile) against a real
 * PostgreSQL and the GitLab mock. One planning-poker session is played through step by step, including a
 * backend restart. Steps build on each other.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlanningPokerE2EIT {

    private static final String PROJECT = "1";
    private static final int ISSUE = 2;

    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final GitLabMockResource GITLAB = new GitLabMockResource();
    private static BackendProcess backend;

    private static Credentials sarah;
    private static Credentials tom;
    private static Credentials lena;
    private static SseClient sarahEvents;
    private static SseClient tomEvents;
    private static SseClient lenaEvents;

    @BeforeAll
    static void startStack() {
        DB.start();
        String gitlabUrl = GITLAB.start().get("gitlab.base-url");
        System.setProperty("gitlab.base-url", gitlabUrl);
        backend = new BackendProcess(Map.of(
                "DB_URL", DB.getJdbcUrl(),
                "DB_USER", DB.getUsername(),
                "DB_PASSWORD", DB.getPassword(),
                "GITLAB_BASE_URL", gitlabUrl,
                "GITLAB_TOKEN", GitLabMockResource.TOKEN));
        backend.start();
        RestAssured.port = backend.port();
    }

    @AfterAll
    static void stopStack() {
        closeStreams();
        if (backend != null) {
            backend.close();
        }
        GITLAB.stop();
        DB.stop();
    }

    @Test
    @Order(1)
    void scrumMasterCreatesSessionAndTeamJoins() {
        sarah = createSession(PROJECT, ISSUE, "Sarah");
        tom = join(sarah.sessionId(), "Tom");
        lena = join(sarah.sessionId(), "Lena");
        sarahEvents = SseClient.connect(sarah);
        tomEvents = SseClient.connect(tom);
        lenaEvents = SseClient.connect(lena);

        JsonPath s = sarahEvents.awaitSnapshot(x -> online(x, "Tom") && online(x, "Lena"));
        assertEquals(List.of("Sarah", "Tom", "Lena"), s.getList("participants.name"));
        assertEquals("SCRUM_MASTER", s.getString("participants.find { it.name == 'Sarah' }.role"));
        assertEquals("ISSUE_SELECTED", s.getString("state"));
        assertEquals(ISSUE, s.getInt("issue.iid"));
        assertNull(tomEvents.awaitFirstSnapshot().get("issue"), "issue hidden from developers before release");
    }

    @Test
    @Order(2)
    void scrumMasterBrowsesGitLabIssues() {
        JsonPath page = issues(sarah, Map.of("perPage", 100)).statusCode(200).extract().jsonPath();
        assertTrue(page.getList("issues.iid", Integer.class).containsAll(List.of(1, 2, 3, 4, 5)));
        assertFalse(page.getList("issues.iid", Integer.class).contains(6), "closed issue not offered");
        issues(tom, Map.of()).statusCode(403);
    }

    @Test
    @Order(3)
    void releaseShowsIssueToEveryone() {
        startRound(sarah).statusCode(204);

        JsonPath t = tomEvents.awaitSnapshot(x -> "VOTING".equals(x.getString("state")));
        assertEquals("REST-Endpunkt für Benutzerprofil", t.getString("issue.title"));
        assertEquals(1, t.getInt("roundNumber"));
        lenaEvents.awaitSnapshot(x -> "VOTING".equals(x.getString("state")));
    }

    @Test
    @Order(4)
    void progressIsVisibleWithoutRevealingCards() {
        vote(tom, "5").statusCode(204);

        JsonPath l = lenaEvents.awaitSnapshot(x -> voted(x, "Tom"));
        assertFalse(voted(l, "Lena"));
        assertFalse(l.prettify().contains("\"5\""), "Tom's card must not leak to Lena");
        JsonPath s = sarahEvents.awaitSnapshot(x -> voted(x, "Tom"));
        assertFalse(s.getBoolean("allVoted"));

        vote(lena, "8").statusCode(204);
        assertTrue(sarahEvents.awaitSnapshot(x -> voted(x, "Lena")).getBoolean("allVoted"));
    }

    @Test
    @Order(5)
    void revealShowsCardsAndEvaluation() {
        reveal(sarah).statusCode(204);

        JsonPath t = tomEvents.awaitSnapshot(x -> "REVEALED".equals(x.getString("state")));
        assertEquals("5", t.getString("result.votes.find { it.name == 'Tom' }.card"));
        assertEquals("8", t.getString("result.votes.find { it.name == 'Lena' }.card"));
        assertEquals(6.5, t.getDouble("result.average"));
        assertEquals(2, t.getList("result.groups").size());
    }

    @Test
    @Order(6)
    void secondRoundForSameIssue() {
        restart(sarah).statusCode(204);

        JsonPath l = lenaEvents.awaitSnapshot(x -> x.getInt("roundNumber") == 2);
        assertEquals("VOTING", l.getString("state"));
        assertNull(l.get("myVote"));
        assertNull(l.get("result"));
        assertEquals(1, l.getList("history").size());

        vote(tom, "5").statusCode(204);
        vote(lena, "5").statusCode(204);
        sarahEvents.awaitSnapshot(x -> Boolean.TRUE.equals(x.get("allVoted")));
        reveal(sarah).statusCode(204);

        JsonPath s = sarahEvents.awaitSnapshot(x -> "REVEALED".equals(x.getString("state")));
        assertEquals("5", s.getString("result.mostFrequent"));
        assertEquals(List.of(Map.of("card", "5", "count", 2)), s.getList("result.groups"));
    }

    @Test
    @Order(7)
    void confirmWritesEstimateToGitLab() {
        confirm(sarah, "5").statusCode(204);

        JsonPath t = tomEvents.awaitSnapshot(x -> "CONFIRMED".equals(x.getString("state")));
        assertEquals("5", t.getString("confirmedCard"));
        assertTrue(t.getList("issue.labels").contains("SP::5"));
        assertTrue(MockGitLab.labels(PROJECT, ISSUE).contains("SP::5"));
        List<String> notes = MockGitLab.notes(PROJECT, ISSUE);
        assertEquals(1, notes.size());
        assertTrue(notes.getFirst().contains("**5**"), notes.getFirst());
    }

    @Test
    @Order(8)
    void sessionSurvivesBackendRestart() {
        closeStreams();
        backend.restart();

        JsonPath s = snapshot(sarah);
        assertEquals("CONFIRMED", s.getString("state"));
        assertEquals("5", s.getString("confirmedCard"));
        assertEquals(2, s.getInt("roundNumber"));
        assertEquals(List.of("Sarah", "Tom", "Lena"), s.getList("participants.name"));
        assertEquals(1, s.getList("history").size());
        assertEquals("8", s.getString("history[0].votes.find { it.name == 'Lena' }.card"));
        assertFalse(online(s, "Tom"), "nobody is connected right after the restart");
        assertEquals(sarah.participantId(), snapshot(tom).getString("participants.find { it.name == 'Sarah' }.id"));
    }

    @Test
    @Order(9)
    void sessionContinuesAfterRestart() {
        sarahEvents = SseClient.connect(sarah);
        tomEvents = SseClient.connect(tom);
        assertEquals("CONFIRMED", tomEvents.awaitFirstSnapshot().getString("state"));
        sarahEvents.awaitSnapshot(x -> online(x, "Tom"));

        selectIssue(sarah, 3).statusCode(204);
        startRound(sarah).statusCode(204);
        tomEvents.awaitSnapshot(x -> "VOTING".equals(x.getString("state")) && x.getInt("issue.iid") == 3);
        vote(tom, "13").statusCode(204);

        JsonPath s = sarahEvents.awaitSnapshot(x -> "VOTING".equals(x.getString("state")) && voted(x, "Tom"));
        assertEquals(3, s.getInt("issue.iid"));
        assertFalse(voted(s, "Lena"));
        assertEquals(2, s.getList("history").size(), "confirmed round 2 joined the protocol");
        assertEquals("5", s.getString("history[1].confirmedCard"));
    }

    private static boolean online(JsonPath s, String name) {
        return Boolean.TRUE.equals(s.get("participants.find { it.name == '" + name + "' }.online"));
    }

    private static boolean voted(JsonPath s, String name) {
        return Boolean.TRUE.equals(s.get("participants.find { it.name == '" + name + "' }.hasVoted"));
    }

    private static void closeStreams() {
        for (SseClient c : new SseClient[] {sarahEvents, tomEvents, lenaEvents}) {
            if (c != null) {
                c.close();
            }
        }
        sarahEvents = tomEvents = lenaEvents = null;
    }
}
