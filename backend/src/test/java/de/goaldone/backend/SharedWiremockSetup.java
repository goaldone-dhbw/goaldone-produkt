package de.goaldone.backend;

import com.github.tomakehurst.wiremock.WireMockServer;

public class SharedWiremockSetup {
    private static final WireMockServer wireMockServer = new WireMockServer(8099);

    static {
        // Start WireMock before Spring context initialization to allow StartupValidator to connect
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
    }

    public static WireMockServer getSharedWireMockServer() {
        return wireMockServer;
    }
}
