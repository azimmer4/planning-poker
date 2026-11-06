package com.example.poker.support;

import com.example.poker.auth.RequiresParticipant;
import com.example.poker.session.Role;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** Test-only endpoint to verify role enforcement independently of real control endpoints. */
@Path("/api/sessions/{id}/__test")
public class RoleProbeTestResource {

    @GET
    @Path("/scrum-master-only")
    @RequiresParticipant(Role.SCRUM_MASTER)
    public String scrumMasterOnly(@PathParam("id") String id) {
        return "ok";
    }
}
