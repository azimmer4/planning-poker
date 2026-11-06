package com.example.poker.gitlab;

import com.example.poker.api.ApiException;
import com.example.poker.domain.Card;
import com.example.poker.gitlab.GitLabClient.LabelUpdate;
import com.example.poker.gitlab.GitLabClient.Note;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.function.Supplier;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * All GitLab access. A missing project/issue becomes 400 (bad user input),
 * any other GitLab problem (unreachable, wrong token, 5xx) becomes 502.
 */
@ApplicationScoped
public class GitLabService {

    public static final String ESTIMATE_LABEL_PREFIX = "SP::";

    public record IssuePage(List<GitLabIssue> issues, Integer nextPage) {
    }

    @Inject
    @RestClient
    GitLabClient client;

    public IssuePage listOpenIssues(String projectId, int page, int perPage) {
        var response = call(() -> client.listIssues(projectId, "opened", page, perPage), "GitLab project " + projectId + " not found");
        String next = response.getHeaderString("X-Next-Page");
        return new IssuePage(response.getEntity(), next == null || next.isBlank() ? null : Integer.valueOf(next));
    }

    public void requireProject(String projectId) {
        listOpenIssues(projectId, 1, 1);
    }

    public GitLabIssue getIssue(String projectId, int iid) {
        return call(() -> client.getIssue(projectId, iid), "Issue #" + iid + " not found in GitLab project " + projectId);
    }

    /**
     * Stores the agreed estimate: numeric cards replace any {@code SP::*} label with {@code SP::<value>};
     * every card adds the note. Safe to retry after a partial failure.
     *
     * @return the issue labels after the update
     */
    public List<String> writeEstimate(String projectId, int iid, Card card, String note) {
        List<String> labels = getIssue(projectId, iid).labels();
        if (card.isNumeric()) {
            String target = ESTIMATE_LABEL_PREFIX + card.value();
            String remove = String.join(",", labels.stream()
                    .filter(l -> l.startsWith(ESTIMATE_LABEL_PREFIX) && !l.equals(target))
                    .toList());
            labels = call(() -> client.updateLabels(projectId, iid, new LabelUpdate(target, remove.isEmpty() ? null : remove)), null)
                    .labels();
        }
        call(() -> {
            client.addNote(projectId, iid, new Note(note));
            return null;
        }, null);
        return labels;
    }

    private static <T> T call(Supplier<T> request, String notFoundMessage) {
        try {
            return request.get();
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 404 && notFoundMessage != null) {
                throw ApiException.badRequest(notFoundMessage);
            }
            throw ApiException.badGateway("GitLab request failed with HTTP " + status);
        } catch (ProcessingException e) {
            throw ApiException.badGateway("GitLab is not reachable");
        }
    }
}
