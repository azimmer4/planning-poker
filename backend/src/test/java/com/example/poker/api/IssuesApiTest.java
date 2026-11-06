package com.example.poker.api;

import static com.example.poker.support.PokerApi.createSession;
import static com.example.poker.support.PokerApi.issues;
import static com.example.poker.support.PokerApi.join;
import static com.example.poker.support.PokerApi.selectIssue;
import static com.example.poker.support.PokerApi.snapshot;
import static com.example.poker.support.PokerApi.startRound;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.poker.support.MockGitLab;
import com.example.poker.support.PokerApi.Credentials;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class IssuesApiTest {

    Credentials sm;
    Credentials tom;

    @BeforeEach
    void setUp() {
        MockGitLab.reset();
        sm = createSession("1", null, "Sarah");
        tom = join(sm.sessionId(), "Tom");
    }

    @Nested
    class ListIssues {

        @Test
        void returnsOpenIssuesOfTheSessionProject() {
            issues(sm, Map.of()).statusCode(200)
                    .body("issues.iid", contains(1, 2, 3, 4, 5))
                    .body("issues[1].title", equalTo("REST-Endpunkt für Benutzerprofil"))
                    .body("issues[1].description", equalTo("GET /users/{id} bereitstellen."))
                    .body("issues[0].labels", contains("frontend", "SP::3"))
                    .body("issues[0].webUrl", equalTo("http://gitlab-mock/projects/1/-/issues/1"))
                    .body("nextPage", nullValue());
        }

        @Test
        void isPaginated() {
            issues(sm, Map.of("page", 1, "perPage", 2)).statusCode(200)
                    .body("issues.iid", contains(1, 2))
                    .body("nextPage", equalTo(2));
            issues(sm, Map.of("page", 3, "perPage", 2)).statusCode(200)
                    .body("issues.iid", contains(5))
                    .body("nextPage", nullValue());
        }

        @Test
        void usesTheProjectOfTheSession() {
            Credentials other = createSession("2", null, "Olga");

            issues(other, Map.of()).statusCode(200).body("issues.iid", contains(1, 2));
        }

        @Test
        void invalidPagingIs400() {
            issues(sm, Map.of("page", 0)).statusCode(400);
            issues(sm, Map.of("perPage", 101)).statusCode(400);
        }

        @Test
        void developerGets403() {
            issues(tom, Map.of()).statusCode(403);
        }
    }

    @Nested
    class IssueDetailsInSnapshot {

        @Test
        void selectedIssueIsLoadedFromGitLab() {
            selectIssue(sm, 2).statusCode(204);

            JsonPath s = snapshot(sm);
            assertEquals(2, s.getInt("issue.iid"));
            assertEquals("REST-Endpunkt für Benutzerprofil", s.getString("issue.title"));
            assertEquals("GET /users/{id} bereitstellen.", s.getString("issue.description"));
            assertEquals("http://gitlab-mock/projects/1/-/issues/2", s.getString("issue.webUrl"));
            assertEquals(List.of("backend"), s.getList("issue.labels"));
        }

        @Test
        void issueGivenOnCreationIsLoadedFromGitLab() {
            Credentials other = createSession("1", 3, "Olga");

            assertEquals("Datenbank-Migration für Rechte", snapshot(other).getString("issue.title"));
        }

        @Test
        void developerSeesDetailsOnlyAfterRelease() {
            selectIssue(sm, 2).statusCode(204);
            assertNull(snapshot(tom).get("issue"));

            startRound(sm).statusCode(204);

            assertEquals("REST-Endpunkt für Benutzerprofil", snapshot(tom).getString("issue.title"));
        }

        @Test
        void unknownIssueIs400AndKeepsState() {
            selectIssue(sm, 2).statusCode(204);

            selectIssue(sm, 99).statusCode(400);

            assertEquals(2, snapshot(sm).getInt("issue.iid"));
        }

        @Test
        void unknownIssueOnCreationIs400() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("projectId", "1", "issueIid", 99, "name", "Sarah"))
                    .post("/api/sessions").then().statusCode(400);
        }

        @Test
        void unknownProjectOnCreationIs400() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("projectId", "does-not-exist", "name", "Sarah"))
                    .post("/api/sessions").then().statusCode(400);
        }
    }
}
