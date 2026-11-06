package com.example.poker.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Starts the gitlab-mock (Node) on a random port and points the backend at it. */
public class GitLabMockResource implements QuarkusTestResourceLifecycleManager {

    public static final String TOKEN = "fake-token";
    private static final Pattern PORT = Pattern.compile("listening on (\\d+)");

    private Process process;

    @Override
    public Map<String, String> start() {
        File script = new File("../gitlab-mock/src/server.js").getAbsoluteFile();
        ProcessBuilder builder = new ProcessBuilder("node", script.getPath());
        builder.environment().put("PORT", "0");
        builder.environment().put("MOCK_EXPECTED_TOKEN", TOKEN);
        builder.redirectErrorStream(true);
        try {
            process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = PORT.matcher(line);
                if (matcher.find()) {
                    String baseUrl = "http://127.0.0.1:" + matcher.group(1);
                    return Map.of(
                            "gitlab.base-url", baseUrl,
                            "gitlab.token", TOKEN,
                            "quarkus.rest-client.gitlab.url", baseUrl + "/api/v4");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not start gitlab-mock (is node installed?)", e);
        }
        throw new IllegalStateException("gitlab-mock did not report a port");
    }

    @Override
    public void stop() {
        if (process != null) {
            process.destroy();
        }
    }
}
