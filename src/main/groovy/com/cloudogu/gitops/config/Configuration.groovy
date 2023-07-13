package com.cloudogu.gitops.config

import jakarta.inject.Singleton

@Singleton
class Configuration {
    private final Map config

    Configuration(Map config) {
        this.config = config
    }

    Map getConfig() {
        return this.config
    }
}
