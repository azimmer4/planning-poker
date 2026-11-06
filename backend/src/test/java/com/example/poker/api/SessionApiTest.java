package com.example.poker.api;

import static com.example.poker.support.PokerApi.as;
import static com.example.poker.support.PokerApi.createSession;
import static com.example.poker.support.PokerApi.join;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.poker.support.PokerApi;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class SessionApiTest {

    // Injected here because Quarkus does not inject into @Nested classes
    @Inject
    EntityManager em;

    @Nested
    class CreateSession {

        @Test
        void returnsCredentials() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("projectId", "1", "issueIid", 1, "name", "Sarah"))
                    .when().post("/api/sessions")
                    .then().statusCode(201)
                    .body("sessionId", not(nullValue()))
                    .body("participantId", not(nullValue()))
                    .body("token", not(nullValue()));
        }

        @Test
        void creatorBecomesScrumMaster() {
            var sm = createSession("Sarah");

            as(sm).get("/api/sessions/{id}", sm.sessionId())
                    .then().statusCode(200)
                    .body("sessionId", equalTo(sm.sessionId()))
                    .body("projectId", equalTo("1"))
                    .body("me.id", equalTo(sm.participantId()))
                    .body("me.role", equalTo("SCRUM_MASTER"))
                    .body("participants.name", contains("Sarah"))
                    .body("participants.role", contains("SCRUM_MASTER"));
        }

        @Test
        void withoutIssueStartsWithoutSelection() {
            var sm = createSession("1", null, "Sarah");

            as(sm).get("/api/sessions/{id}", sm.sessionId())
                    .then().body("state", equalTo("NO_ISSUE"));
        }

        @Test
        void withIssuePreselectsIt() {
            var sm = createSession("1", 3, "Sarah");

            as(sm).get("/api/sessions/{id}", sm.sessionId())
                    .then().body("state", equalTo("ISSUE_SELECTED"));
        }

        @Test
        void trimsName() {
            var sm = createSession("  Sarah  ");

            as(sm).get("/api/sessions/{id}", sm.sessionId())
                    .then().body("participants.name", contains("Sarah"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"projectId\":\"1\",\"name\":\"\"}",
                "{\"projectId\":\"1\",\"name\":\"   \"}",
                "{\"projectId\":\"1\"}",
                "{\"name\":\"Sarah\"}",
                "{\"projectId\":\" \",\"name\":\"Sarah\"}",
                "{\"projectId\":\"1\",\"issueIid\":0,\"name\":\"Sarah\"}",
                "{\"projectId\":\"1\",\"name\":\"ThisNameIsWayTooLongForTheParticipantList\"}"
        })
        void rejectsInvalidInput(String body) {
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/sessions")
                    .then().statusCode(400);
        }
    }

    @Nested
    class JoinSession {

        @Test
        void returnsOwnCredentials() {
            var sm = createSession("Sarah");
            var dev = join(sm.sessionId(), "Tom");

            assertNotNull(dev.token());
            assertNotEquals(sm.participantId(), dev.participantId());
            assertNotEquals(sm.token(), dev.token());
        }

        @Test
        void joinerBecomesDeveloperAndIsVisibleToEveryoneInJoinOrder() {
            var sm = createSession("Sarah");
            var tom = join(sm.sessionId(), "Tom");
            join(sm.sessionId(), "Lena");

            as(tom).get("/api/sessions/{id}", sm.sessionId())
                    .then().statusCode(200)
                    .body("me.role", equalTo("DEVELOPER"))
                    .body("participants.name", contains("Sarah", "Tom", "Lena"))
                    .body("participants.role", contains("SCRUM_MASTER", "DEVELOPER", "DEVELOPER"));
            as(sm).get("/api/sessions/{id}", sm.sessionId())
                    .then().body("participants.name", contains("Sarah", "Tom", "Lena"));
        }

        @Test
        void unknownSessionIs404() {
            given().contentType(ContentType.JSON).body(Map.of("name", "Tom"))
                    .when().post("/api/sessions/{id}/join", UUID.randomUUID())
                    .then().statusCode(404);
        }

        @Test
        void malformedSessionIdIs404() {
            given().contentType(ContentType.JSON).body(Map.of("name", "Tom"))
                    .when().post("/api/sessions/{id}/join", "not-a-uuid")
                    .then().statusCode(404);
        }

        @Test
        void blankNameIs400() {
            var sm = createSession("Sarah");

            given().contentType(ContentType.JSON).body(Map.of("name", " "))
                    .when().post("/api/sessions/{id}/join", sm.sessionId())
                    .then().statusCode(400);
        }

        @Test
        void duplicateNameIgnoringCaseIs409() {
            var sm = createSession("Sarah");
            join(sm.sessionId(), "Tom");

            given().contentType(ContentType.JSON).body(Map.of("name", " tom "))
                    .when().post("/api/sessions/{id}/join", sm.sessionId())
                    .then().statusCode(409);
        }

        @Test
        void sameNameInDifferentSessionsIsAllowed() {
            join(createSession("Sarah").sessionId(), "Tom");
            join(createSession("Sarah").sessionId(), "Tom");
        }
    }

    @Nested
    class Authentication {

        @Test
        void missingTokenIs401() {
            var sm = createSession("Sarah");

            given().get("/api/sessions/{id}", sm.sessionId()).then().statusCode(401);
        }

        @Test
        void wrongTokenIs401() {
            var sm = createSession("Sarah");

            given().header(PokerApi.TOKEN_HEADER, "invalid")
                    .get("/api/sessions/{id}", sm.sessionId())
                    .then().statusCode(401);
        }

        @Test
        void tokenOfAnotherSessionIs401() {
            var own = createSession("Sarah");
            var other = createSession("Max");

            as(other).get("/api/sessions/{id}", own.sessionId()).then().statusCode(401);
        }

        @Test
        void unknownSessionIs404() {
            var sm = createSession("Sarah");

            as(sm).get("/api/sessions/{id}", UUID.randomUUID()).then().statusCode(404);
        }

        @Test
        void developerOnScrumMasterEndpointIs403() {
            var sm = createSession("Sarah");
            var dev = join(sm.sessionId(), "Tom");

            as(dev).get("/api/sessions/{id}/__test/scrum-master-only", sm.sessionId()).then().statusCode(403);
        }

        @Test
        void scrumMasterOnScrumMasterEndpointIsAllowed() {
            var sm = createSession("Sarah");

            as(sm).get("/api/sessions/{id}/__test/scrum-master-only", sm.sessionId()).then().statusCode(200);
        }
    }

    @Nested
    class Persistence {

        @Test
        void tokenIsNotStoredInPlaintext() {
            var sm = createSession("Sarah");

            Long plaintextMatches = em.createQuery(
                            "select count(p) from Participant p where p.tokenHash = :t", Long.class)
                    .setParameter("t", sm.token())
                    .getSingleResult();
            String storedHash = em.createQuery(
                            "select p.tokenHash from Participant p where p.id = :id", String.class)
                    .setParameter("id", UUID.fromString(sm.participantId()))
                    .getSingleResult();

            org.junit.jupiter.api.Assertions.assertEquals(0L, plaintextMatches);
            assertNotNull(storedHash);
        }
    }
}
