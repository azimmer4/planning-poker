package com.example.poker.gitlab;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestResponse;

/** GitLab REST API v4, issues of a project. */
@RegisterRestClient(configKey = "gitlab")
@RegisterClientHeaders(GitLabAuthHeaders.class)
@Path("/projects/{projectId}/issues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitLabClient {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record LabelUpdate(@JsonProperty("add_labels") String addLabels,
                       @JsonProperty("remove_labels") String removeLabels) {
    }

    record Note(String body) {
    }

    @GET
    RestResponse<List<GitLabIssue>> listIssues(@PathParam("projectId") String projectId,
                                               @QueryParam("state") String state,
                                               @QueryParam("page") int page,
                                               @QueryParam("per_page") int perPage);

    @GET
    @Path("/{iid}")
    GitLabIssue getIssue(@PathParam("projectId") String projectId, @PathParam("iid") int iid);

    @PUT
    @Path("/{iid}")
    GitLabIssue updateLabels(@PathParam("projectId") String projectId, @PathParam("iid") int iid, LabelUpdate update);

    @POST
    @Path("/{iid}/notes")
    void addNote(@PathParam("projectId") String projectId, @PathParam("iid") int iid, Note note);
}
