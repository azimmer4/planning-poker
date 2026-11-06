package com.example.poker.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.ConfigProvider;

/** Test-side access to the gitlab-mock control endpoints (/__test/*). */
public final class MockGitLab {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockGitLab() {
    }

    public static String baseUrl() {
        return ConfigProvider.getConfig().getValue("gitlab.base-url", String.class);
    }

    public static void reset() {
        post("/__test/reset");
    }

    /** The next write (PUT/POST) against the mock fails with HTTP 500. */
    public static void failNext() {
        post("/__test/fail-next");
    }

    public static List<String> labels(String projectId, int iid) {
        return texts(issue(projectId, iid).get("labels"), null);
    }

    public static List<String> notes(String projectId, int iid) {
        return texts(issue(projectId, iid).get("notes"), "body");
    }

    private static JsonNode issue(String projectId, int iid) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl() + "/__test/state")).build();
            var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return MAPPER.readTree(response.body()).get(projectId).get(String.valueOf(iid));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> result = new ArrayList<>();
        array.forEach(node -> result.add(field == null ? node.asText() : node.get(field).asText()));
        return result;
    }

    private static void post(String path) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
