package com.example.poker.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Test helper for creating and joining sessions through the public API. */
public final class PokerApi {

    public static final String TOKEN_HEADER = "X-Participant-Token";

    public record Credentials(String sessionId, String participantId, String token) {
    }

    private PokerApi() {
    }

    public static Credentials createSession(String projectId, Integer issueIid, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("projectId", projectId);
        body.put("issueIid", issueIid);
        body.put("name", name);
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/api/sessions")
                .then().statusCode(201)
                .extract().as(Credentials.class);
    }

    public static Credentials createSession(String name) {
        return createSession("1", null, name);
    }

    public static Credentials join(String sessionId, String name) {
        return given().contentType(ContentType.JSON).body(Map.of("name", name))
                .when().post("/api/sessions/{id}/join", sessionId)
                .then().statusCode(201)
                .extract().as(Credentials.class);
    }

    public static RequestSpecification as(Credentials credentials) {
        return given().header(TOKEN_HEADER, credentials.token()).contentType(ContentType.JSON);
    }

    public static JsonPath snapshot(Credentials c) {
        return as(c).get("/api/sessions/{id}", c.sessionId()).then().statusCode(200).extract().jsonPath();
    }

    public static ValidatableResponse selectIssue(Credentials c, Object issueIid) {
        return as(c).body(Collections.singletonMap("issueIid", issueIid))
                .put("/api/sessions/{id}/issue", c.sessionId()).then();
    }

    public static ValidatableResponse startRound(Credentials c) {
        return as(c).post("/api/sessions/{id}/rounds", c.sessionId()).then();
    }

    public static ValidatableResponse vote(Credentials c, String card) {
        return as(c).body(Collections.singletonMap("card", card))
                .put("/api/sessions/{id}/votes", c.sessionId()).then();
    }

    public static ValidatableResponse reveal(Credentials c) {
        return as(c).post("/api/sessions/{id}/reveal", c.sessionId()).then();
    }

    public static ValidatableResponse restart(Credentials c) {
        return as(c).post("/api/sessions/{id}/rounds/restart", c.sessionId()).then();
    }

    public static ValidatableResponse leave(Credentials c) {
        return as(c).post("/api/sessions/{id}/leave", c.sessionId()).then();
    }

    public static ValidatableResponse confirm(Credentials c, String card) {
        return as(c).body(Collections.singletonMap("card", card))
                .post("/api/sessions/{id}/confirm", c.sessionId()).then();
    }

    public static ValidatableResponse issues(Credentials c, Map<String, ?> query) {
        return as(c).queryParams(query).get("/api/sessions/{id}/issues", c.sessionId()).then();
    }
}
