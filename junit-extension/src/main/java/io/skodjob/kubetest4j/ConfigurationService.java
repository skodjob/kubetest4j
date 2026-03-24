/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j;

import io.skodjob.kubetest4j.annotations.KubernetesTest;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Arrays;

/**
 * Manages test configuration creation and parsing.
 * This class centralizes all configuration-related logic and provides
 * clean separation between configuration parsing and test execution.
 */
class ConfigurationService {

    private final ContextStoreHelper contextStoreHelper;

    /**
     * Creates a new ConfigurationManager with the given kubeContext store helper.
     *
     * @param contextStoreHelper provides access to extension kubeContext storage
     */
    ConfigurationService(ContextStoreHelper contextStoreHelper) {
        this.contextStoreHelper = contextStoreHelper;
    }

    /**
     * Gets the @KubernetesTest annotation from the test class.
     */
    public KubernetesTest getKubernetesTestAnnotation(ExtensionContext context) {
        return context.getRequiredTestClass().getAnnotation(KubernetesTest.class);
    }

    /**
     * Creates and stores a TestConfig based on the @KubernetesTest annotation.
     * This convenience method combines annotation retrieval, config creation, and storage.
     */
    public TestConfig createAndStoreTestConfig(ExtensionContext context) {
        KubernetesTest testAnnotation = getKubernetesTestAnnotation(context);
        if (testAnnotation == null) {
            throw new IllegalStateException("@KubernetesTest annotation not found on test class");
        }

        TestConfig testConfig = createTestConfig(testAnnotation);
        contextStoreHelper.putTestConfig(context, testConfig);
        return testConfig;
    }

    /**
     * Creates a TestConfig from a @KubernetesTest annotation.
     */
    public TestConfig createTestConfig(KubernetesTest annotation) {
        return new TestConfig(
            annotation.cleanup(),
            annotation.storeYaml(),
            annotation.yamlStorePath(),
            annotation.visualSeparatorChar(),
            annotation.visualSeparatorLength(),
            annotation.collectLogs(),
            annotation.logCollectionStrategy(),
            annotation.logCollectionPath(),
            annotation.collectPreviousLogs(),
            Arrays.asList(annotation.collectNamespacedResources()),
            Arrays.asList(annotation.collectClusterWideResources())
        );
    }

    /**
     * Gets the stored TestConfig from the extension kubeContext.
     */
    public TestConfig getTestConfig(ExtensionContext context) {
        return contextStoreHelper.getTestConfig(context);
    }
}
