package com.example.poker.auth;

import com.example.poker.api.ApiException;
import com.example.poker.session.Participant;
import com.example.poker.session.PokerSession;
import com.example.poker.session.Role;
import com.example.poker.session.SessionIds;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.UUID;

@Provider
@RequiresParticipant
@Priority(Priorities.AUTHENTICATION)
public class ParticipantAuthFilter implements ContainerRequestFilter {

    public static final String TOKEN_HEADER = "X-Participant-Token";

    @Context
    ResourceInfo resourceInfo;

    @Inject
    TokenService tokenService;

    @Inject
    CurrentParticipant currentParticipant;

    @Override
    public void filter(ContainerRequestContext ctx) {
        UUID sessionId = SessionIds.parse(ctx.getUriInfo().getPathParameters().getFirst("id"));
        if (PokerSession.findById(sessionId) == null) {
            throw ApiException.notFound("Session not found");
        }

        RequiresParticipant annotation = annotation();
        String token = ctx.getHeaderString(TOKEN_HEADER);
        if ((token == null || token.isBlank()) && annotation != null && annotation.queryToken()) {
            token = ctx.getUriInfo().getQueryParameters().getFirst("token");
        }
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("Missing participant token");
        }
        Participant participant = Participant.findBySessionAndTokenHash(sessionId, tokenService.hash(token))
                .orElseThrow(() -> ApiException.unauthorized("Invalid participant token"));

        Role[] required = annotation == null ? new Role[0] : annotation.value();
        if (required.length > 0 && Arrays.stream(required).noneMatch(r -> r == participant.role)) {
            throw ApiException.forbidden("Not allowed for role " + participant.role);
        }
        currentParticipant.set(participant);
    }

    private RequiresParticipant annotation() {
        RequiresParticipant annotation = resourceInfo.getResourceMethod().getAnnotation(RequiresParticipant.class);
        return annotation != null ? annotation : resourceInfo.getResourceClass().getAnnotation(RequiresParticipant.class);
    }
}
