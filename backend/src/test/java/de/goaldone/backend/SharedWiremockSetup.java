package de.goaldone.backend;

import com.github.tomakehurst.wiremock.WireMockServer;

public class SharedWiremockSetup {
    private static final WireMockServer wireMockServer = new WireMockServer(8099);

    public static WireMockServer getSharedWireMockServer() {
        return wireMockServer;
    }
}
