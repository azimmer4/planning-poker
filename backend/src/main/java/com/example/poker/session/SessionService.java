package com.example.poker.session;

import com.example.poker.api.ApiException;
import com.example.poker.api.Dtos.Credentials;
import com.example.poker.auth.TokenService;
import com.example.poker.events.SessionChanged;
import com.example.poker.gitlab.GitLabService;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SessionService {

    @Inject
    TokenService tokenService;

    @Inject
    Event<SessionChanged> changed;

    @Inject
    GitLabService gitLab;

    @Transactional
    public Credentials create(String projectId, Integer issueIid, String name) {
        PokerSession session = new PokerSession();
        session.id = UUID.randomUUID();
        session.projectId = projectId.trim();
        if (issueIid == null) {
            gitLab.requireProject(session.projectId);
            session.state = SessionState.NO_ISSUE;
        } else {
            session.selectIssue(gitLab.getIssue(session.projectId, issueIid));
            session.state = SessionState.ISSUE_SELECTED;
        }
        session.createdAt = Instant.now();
        session.persist();

        return addParticipant(session.id, name, Role.SCRUM_MASTER);
    }

    @Transactional
    public Credentials join(UUID sessionId, String name) {
        if (PokerSession.findById(sessionId) == null) {
            throw ApiException.notFound("Session not found");
        }
        Credentials credentials = addParticipant(sessionId, name, Role.DEVELOPER);
        changed.fire(new SessionChanged(sessionId));
        return credentials;
    }

    private Credentials addParticipant(UUID sessionId, String rawName, Role role) {
        String name = rawName.trim();
        if (Participant.nameTaken(sessionId, name)) {
            throw ApiException.conflict("Name already taken in this session");
        }
        String token = tokenService.generate();

        Participant participant = new Participant();
        participant.id = UUID.randomUUID();
        participant.sessionId = sessionId;
        participant.name = name;
        participant.role = role;
        participant.tokenHash = tokenService.hash(token);
        participant.joinedAt = Instant.now();
        participant.persist();

        return new Credentials(sessionId.toString(), participant.id.toString(), token);
    }
}
