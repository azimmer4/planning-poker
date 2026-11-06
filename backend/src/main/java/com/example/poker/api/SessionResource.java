package com.example.poker.api;

import com.example.poker.api.Dtos.ConfirmRequest;
import com.example.poker.api.Dtos.CreateSessionRequest;
import com.example.poker.api.Dtos.Credentials;
import com.example.poker.api.Dtos.IssueListView;
import com.example.poker.api.Dtos.IssueView;
import com.example.poker.api.Dtos.JoinSessionRequest;
import com.example.poker.api.Dtos.SelectIssueRequest;
import com.example.poker.api.Dtos.SessionSnapshot;
import com.example.poker.api.Dtos.VoteRequest;
import com.example.poker.auth.CurrentParticipant;
import com.example.poker.auth.RequiresParticipant;
import com.example.poker.events.SessionEvents;
import com.example.poker.gitlab.GitLabService;
import com.example.poker.session.PokerSession;
import com.example.poker.session.Participant;
import com.example.poker.session.Role;
import com.example.poker.session.RoundService;
import com.example.poker.session.SessionIds;
import com.example.poker.session.SessionService;
import com.example.poker.session.SnapshotService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/api/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SessionResource {

    @Inject
    SessionService sessions;

    @Inject
    RoundService rounds;

    @Inject
    SnapshotService snapshots;

    @Inject
    SessionEvents events;

    @Inject
    GitLabService gitLab;

    @Inject
    CurrentParticipant current;

    @POST
    @ResponseStatus(201)
    public Credentials create(@Valid @NotNull CreateSessionRequest request) {
        return sessions.create(request.projectId(), request.issueIid(), request.name());
    }

    @POST
    @Path("/{id}/join")
    @ResponseStatus(201)
    public Credentials join(@PathParam("id") String id, @Valid @NotNull JoinSessionRequest request) {
        return sessions.join(SessionIds.parse(id), request.name());
    }

    @GET
    @Path("/{id}")
    @RequiresParticipant
    public SessionSnapshot get(@PathParam("id") String id) {
        return snapshots.snapshot(current.get());
    }

    /** Real-time stream: event {@code snapshot} with the full state for this participant after every change. */
    @GET
    @Path("/{id}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RequiresParticipant(queryToken = true)
    @Blocking
    public Multi<OutboundSseEvent> events(@PathParam("id") String id, @Context Sse sse) {
        Participant participant = current.get();
        return Multi.createFrom().emitter(emitter ->
                events.subscribe(participant.sessionId, participant.id, sse, emitter));
    }

    /** Open issues of the session's GitLab project, for the scrum master's issue picker. */
    @GET
    @Path("/{id}/issues")
    @RequiresParticipant(Role.SCRUM_MASTER)
    public IssueListView issues(@PathParam("id") String id,
                                @QueryParam("page") @DefaultValue("1") @Min(1) int page,
                                @QueryParam("perPage") @DefaultValue("20") @Min(1) @Max(100) int perPage) {
        PokerSession session = PokerSession.findById(current.get().sessionId);
        var result = gitLab.listOpenIssues(session.projectId, page, perPage);
        var issues = result.issues().stream()
                .map(i -> new IssueView(i.iid(), i.title(), i.description(), i.webUrl(), i.labels()))
                .toList();
        return new IssueListView(issues, result.nextPage());
    }

    @PUT
    @Path("/{id}/issue")
    @RequiresParticipant(Role.SCRUM_MASTER)
    public void selectIssue(@PathParam("id") String id, @Valid @NotNull SelectIssueRequest request) {
        rounds.selectIssue(current.get(), request.issueIid());
    }

    @POST
    @Path("/{id}/rounds")
    @RequiresParticipant(Role.SCRUM_MASTER)
    public void startRound(@PathParam("id") String id) {
        rounds.startRound(current.get());
    }

    @POST
    @Path("/{id}/rounds/restart")
    @RequiresParticipant(Role.SCRUM_MASTER)
    public void restartRound(@PathParam("id") String id) {
        rounds.restart(current.get());
    }

    @PUT
    @Path("/{id}/votes")
    @RequiresParticipant(Role.DEVELOPER)
    public void vote(@PathParam("id") String id, @Valid @NotNull VoteRequest request) {
        rounds.vote(current.get(), request.card());
    }

    @POST
    @Path("/{id}/reveal")
    @RequiresParticipant(Role.SCRUM_MASTER)
    public void reveal(@PathParam("id") String id) {
        rounds.reveal(current.get());
    }

    @POST
    @Path("/{id}/confirm")
    @RequiresParticipant(Role.SCRUM_MASTER)
    public void confirm(@PathParam("id") String id, @Valid @NotNull ConfirmRequest request) {
        rounds.confirm(current.get(), request.card());
    }

    @POST
    @Path("/{id}/leave")
    @RequiresParticipant
    public void leave(@PathParam("id") String id) {
        rounds.leave(current.get());
    }
}
