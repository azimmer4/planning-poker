package com.example.poker.gitlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.poker.support.GitLabMockResource;
import com.example.poker.support.MockGitLab;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(GitLabMockResource.class)
class GitLabMockInfrastructureTest {

    @ConfigProperty(name = "gitlab.base-url")
    String baseUrl;

    @ConfigProperty(name = "gitlab.token")
    String token;

    @BeforeEach
    void reset() {
        MockGitLab.reset();
    }

    @Test
    void backendIsConfiguredWithFakeTokenAndMockUrl() {
        assertEquals("fake-token", token);
        assertTrue(baseUrl.startsWith("http://127.0.0.1:"), baseUrl);
    }

    @Test
    void mockAcceptsConfiguredTokenAndRejectsOthers() throws Exception {
        assertEquals(200, get("/api/v4/projects/1/issues", token).statusCode());
        assertEquals(401, get("/api/v4/projects/1/issues", "wrong").statusCode());
    }

    @Test
    void stateHelperExposesWrittenData() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v4/projects/1/issues/2/notes"))
                .header("PRIVATE-TOKEN", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"body\":\"hello\"}"))
                .build();
        assertEquals(201, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode());

        assertEquals(java.util.List.of("hello"), MockGitLab.notes("1", 2));
        assertTrue(MockGitLab.labels("1", 1).contains("SP::3"));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("PRIVATE-TOKEN", token).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
