package com.example.poker.events;

import java.util.UUID;

/** Fired inside a transaction whenever the visible state of a session changes; broadcast after commit. */
public record SessionChanged(UUID sessionId) {
}
