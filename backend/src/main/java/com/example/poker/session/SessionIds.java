package com.example.poker.session;

import com.example.poker.api.ApiException;
import java.util.UUID;

public final class SessionIds {

    private SessionIds() {
    }

    /** Parses a session id from a path; malformed ids are treated as unknown sessions (404). */
    public static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.notFound("Session not found");
        }
    }
}
