package com.example.poker.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ApiExceptionMapper {

    public record ErrorBody(String message) {
    }

    @ServerExceptionMapper
    public Response map(ApiException e) {
        return Response.status(e.status())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorBody(e.getMessage()))
                .build();
    }
}
