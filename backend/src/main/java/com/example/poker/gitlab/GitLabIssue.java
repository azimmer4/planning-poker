package com.example.poker.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Subset of a GitLab issue (API v4) used by the poker. */
public record GitLabIssue(
        int iid,
        String title,
        String description,
        String state,
        @JsonProperty("web_url") String webUrl,
        List<String> labels) {
}
