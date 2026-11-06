package com.example.poker.gitlab;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/** Authenticates every GitLab call with the configured personal access token. */
public class GitLabAuthHeaders implements ClientHeadersFactory {

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incoming,
                                                 MultivaluedMap<String, String> outgoing) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("PRIVATE-TOKEN", ConfigProvider.getConfig().getValue("gitlab.token", String.class));
        return headers;
    }
}
