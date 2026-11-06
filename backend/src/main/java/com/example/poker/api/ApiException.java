package com.example.poker.api;

import jakarta.ws.rs.core.Response.Status;

public class ApiException extends RuntimeException {

    private final Status status;

    public ApiException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public Status status() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(Status.BAD_REQUEST, message);
    }

    public static ApiException badGateway(String message) {
        return new ApiException(Status.BAD_GATEWAY, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(Status.NOT_FOUND, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(Status.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(Status.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(Status.CONFLICT, message);
    }
}
