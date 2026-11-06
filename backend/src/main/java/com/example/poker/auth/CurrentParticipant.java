package com.example.poker.auth;

import com.example.poker.session.Participant;
import jakarta.enterprise.context.RequestScoped;

/** The authenticated participant of the current request. */
@RequestScoped
public class CurrentParticipant {

    private Participant participant;

    public Participant get() {
        if (participant == null) {
            throw new IllegalStateException("No authenticated participant; is the endpoint annotated with @RequiresParticipant?");
        }
        return participant;
    }

    void set(Participant participant) {
        this.participant = participant;
    }
}
