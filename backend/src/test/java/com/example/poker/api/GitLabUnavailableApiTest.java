package com.example.poker.api;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The backend runs with a token the GitLab mock rejects: every GitLab call fails and the API answers 502. */
@QuarkusTest
@TestProfile(GitLabUnavailableApiTest.WrongToken.class)
class GitLabUnavailableApiTest {

    public static class WrongToken implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gitlab.token", "wrong-token");
        }
    }

    @Test
    void creatingASessionWithIssueIs502() {
        given().contentType(ContentType.JSON)
                .body(Map.of("projectId", "1", "issueIid", 1, "name", "Sarah"))
                .post("/api/sessions").then().statusCode(502)
                .body("message", org.hamcrest.Matchers.containsString("GitLab"));
    }

    @Test
    void creatingASessionWithoutIssueIs502() {
        given().contentType(ContentType.JSON)
                .body(Map.of("projectId", "1", "name", "Sarah"))
                .post("/api/sessions").then().statusCode(502);
    }
}
