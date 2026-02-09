/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.listeners;

import io.skodjob.kubetest4j.resources.KubeResourceManager;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * jUnit5 specific class which listening on test callbacks
 */
public class ResourceManagerExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {

    private ResourceManagerExtension() {
        KubeResourceManager.get();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        KubeResourceManager.get().setTestContext(extensionContext);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        KubeResourceManager.get().setTestContext(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        KubeResourceManager.get().setTestContext(extensionContext);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        KubeResourceManager.get().setTestContext(extensionContext);
    }
}
