package com.codewithkelvin.fx;

import org.testcontainers.DockerClientFactory;

/**
 * Lets the integration tests skip themselves on a machine with no Docker
 * daemon, so {@code mvn verify} still passes on a JDK-only laptop. CI has
 * Docker, so nothing merges without them having run.
 */
public final class DockerAvailable {

    private DockerAvailable() {
    }

    public static boolean check() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
