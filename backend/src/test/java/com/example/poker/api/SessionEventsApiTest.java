package com.example.poker.api;

import static com.example.poker.support.PokerApi.createSession;
import static com.example.poker.support.PokerApi.join;
import static com.example.poker.support.PokerApi.leave;
import static com.example.poker.support.PokerApi.reveal;
import static com.example.poker.support.PokerApi.selectIssue;
import static com.example.poker.support.PokerApi.snapshot;
import static com.example.poker.support.PokerApi.startRound;
import static com.example.poker.support.PokerApi.vote;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.poker.support.PokerApi.Credentials;
import com.example.poker.support.SseClient;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SessionEventsApiTest {

    Credentials sm;
    Credentials tom;
    Credentials lena;
    final List<SseClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sm = createSession("1", null, "Sarah");
        tom = join(sm.sessionId(), "Tom");
        lena = join(sm.sessionId(), "Lena");
    }

    @AfterEach
    void closeStreams() {
        clients.forEach(SseClient::close);
    }

    SseClient open(Credentials c) {
        SseClient client = SseClient.connect(c);
        clients.add(client);
        return client;
    }

    static boolean has(JsonPath s, String name, String field, Object expected) {
        Object actual = s.get("participants.find { it.name == '" + name + "' }." + field);
        return expected.equals(actual);
    }

    static boolean contains(JsonPath s, String name) {
        return s.getList("participants.name").contains(name);
    }

    void startVoting() {
        selectIssue(sm, 2).statusCode(204);
        startRound(sm).statusCode(204);
    }

    @Nested
    class Auth {

        @Test
        void missingTokenIs401() {
            given().get("/api/sessions/{id}/events", sm.sessionId()).then().statusCode(401);
        }

        @Test
        void invalidTokenIs401() {
            given().queryParam("token", "nope").get("/api/sessions/{id}/events", sm.sessionId()).then().statusCode(401);
        }

        @Test
        void unknownSessionIs404() {
            given().queryParam("token", sm.token())
                    .get("/api/sessions/{id}/events", "00000000-0000-0000-0000-000000000000")
                    .then().statusCode(404);
        }

        @Test
        void queryTokenIsOnlyAcceptedForTheEventStream() {
            given().queryParam("token", sm.token()).get("/api/sessions/{id}", sm.sessionId()).then().statusCode(401);
        }
    }

    @Nested
    class Snapshots {

        @Test
        void connectDeliversCurrentSnapshotOfTheViewer() {
            JsonPath first = open(tom).awaitFirstSnapshot();

            assertEquals("Tom", first.getString("me.name"));
            assertEquals("NO_ISSUE", first.getString("state"));
            assertEquals(List.of("Sarah", "Tom", "Lena"), first.getList("participants.name"));
        }

        @Test
        void joinIsPushed() {
            SseClient smStream = open(sm);
            join(sm.sessionId(), "Max");

            smStream.awaitSnapshot(s -> contains(s, "Max"));
        }

        @Test
        void releaseIsPushedToDevelopers() {
            SseClient tomStream = open(tom);
            tomStream.awaitFirstSnapshot();

            startVoting();

            JsonPath s = tomStream.awaitSnapshot(x -> "VOTING".equals(x.getString("state")));
            assertEquals(2, s.getInt("issue.iid"));
        }

        @Test
        void voteIsPushedToEveryoneFilteredPerRecipient() {
            startVoting();
            SseClient smStream = open(sm);
            SseClient tomStream = open(tom);
            SseClient lenaStream = open(lena);

            vote(tom, "13").statusCode(204);

            JsonPath seenByLena = lenaStream.awaitSnapshot(s -> has(s, "Tom", "hasVoted", true));
            assertFalse(seenByLena.prettify().contains("\"13\""));
            JsonPath seenBySm = smStream.awaitSnapshot(s -> has(s, "Tom", "hasVoted", true));
            assertFalse(seenBySm.prettify().contains("\"13\""));
            assertEquals(false, seenBySm.get("allVoted"));
            tomStream.awaitSnapshot(s -> "13".equals(s.getString("myVote")));
        }

        @Test
        void scrumMasterIsNotifiedWhenAllVoted() {
            startVoting();
            SseClient smStream = open(sm);
            vote(tom, "5").statusCode(204);
            vote(lena, "8").statusCode(204);

            smStream.awaitSnapshot(s -> Boolean.TRUE.equals(s.get("allVoted")));
        }

        @Test
        void revealIsPushedToEveryone() {
            startVoting();
            vote(tom, "5").statusCode(204);
            SseClient lenaStream = open(lena);

            reveal(sm).statusCode(204);

            JsonPath s = lenaStream.awaitSnapshot(x -> "REVEALED".equals(x.getString("state")));
            assertEquals(List.of("5"), s.getList("result.votes.card"));
        }

        @Test
        void leaveIsPushedAndClosesTheLeaversStream() {
            SseClient smStream = open(sm);
            SseClient tomStream = open(tom);
            smStream.awaitSnapshot(s -> has(s, "Tom", "online", true));

            leave(tom).statusCode(204);

            smStream.awaitSnapshot(s -> !contains(s, "Tom"));
            tomStream.awaitClosedByServer();
        }

        @Test
        void reconnectStartsWithCurrentState() {
            open(tom).close();
            startVoting();
            vote(tom, "5").statusCode(204);

            JsonPath first = open(tom).awaitFirstSnapshot();

            assertEquals("VOTING", first.getString("state"));
            assertEquals("5", first.getString("myVote"));
        }

        @Test
        void otherSessionsReceiveNothing() {
            Credentials other = createSession("Olga");
            SseClient otherStream = open(other);
            otherStream.awaitFirstSnapshot();

            join(sm.sessionId(), "Max");

            otherStream.assertNoSnapshotWithin(Duration.ofMillis(700));
        }

        @Test
        void heartbeatKeepsTheConnectionAlive() {
            open(sm).awaitHeartbeat();
        }
    }

    @Nested
    class Presence {

        @Test
        void participantWithoutStreamIsOffline() {
            assertFalse(snapshot(sm).getBoolean("participants.find { it.name == 'Tom' }.online"));
        }

        @Test
        void openingAStreamMarksParticipantOnline() {
            SseClient smStream = open(sm);
            open(tom);

            smStream.awaitSnapshot(s -> has(s, "Tom", "online", true));
            assertTrue(snapshot(sm).getBoolean("participants.find { it.name == 'Tom' }.online"));
        }

        @Test
        void closingTheStreamMarksParticipantOfflineButKeepsHimCounting() {
            startVoting();
            SseClient smStream = open(sm);
            SseClient tomStream = open(tom);
            smStream.awaitSnapshot(s -> has(s, "Tom", "online", true));

            tomStream.close();
            smStream.awaitSnapshot(s -> has(s, "Tom", "online", false));

            vote(lena, "3").statusCode(204);
            JsonPath s = smStream.awaitSnapshot(x -> has(x, "Lena", "hasVoted", true));
            assertTrue(contains(s, "Tom"));
            assertEquals(false, s.get("allVoted"));
        }
    }
}
