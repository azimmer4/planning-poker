package com.example.poker.auth;

import com.example.poker.session.Role;
import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires a valid participant token ({@code X-Participant-Token}) for the session in path param {@code id}.
 * If roles are given, the participant must have one of them.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresParticipant {

    Role[] value() default {};

    /** Also accept the token as query parameter {@code token} (needed for EventSource, which cannot send headers). */
    boolean queryToken() default false;
}
