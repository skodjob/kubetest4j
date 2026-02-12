/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.listeners;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

/**
 * jUnit specific class which listening on test callbacks
 */
public class TestVisualSeparatorExtension implements BeforeEachCallback, AfterEachCallback,
    BeforeAllCallback, AfterAllCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestVisualSeparatorExtension.class);

    private TestVisualSeparatorExtension() {
        // Private constructor to prevent instantiation
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        logSeparator(extensionContext);
        LOGGER.info("TestClass {} STARTED", extensionContext.getRequiredTestClass().getName());
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        LOGGER.info("TestClass {} FINISHED", extensionContext.getRequiredTestClass().getName());
        logSeparator(extensionContext);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        logSeparator(extensionContext);
        LOGGER.info("Test {}.{} STARTED", extensionContext.getRequiredTestClass().getName(),
            extensionContext.getDisplayName().replace("()", ""));
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        String state = "SUCCEEDED";
        if (extensionContext.getExecutionException().isPresent()) {
            state = "FAILED";
        }

        LOGGER.info("Test {}.{} {}", extensionContext.getRequiredTestClass().getName(),
            extensionContext.getDisplayName().replace("()", ""), state);
        logSeparator(extensionContext);
    }

    private void logSeparator(ExtensionContext extensionContext) {
        Optional<TestVisualSeparator> annotation =
            findAnnotation(extensionContext.getRequiredTestClass(), TestVisualSeparator.class);
        if (annotation.isPresent()) {
            LoggerUtils.logSeparator(annotation.get().separator(), annotation.get().lineLength());
        } else {
            LoggerUtils.logSeparator();
        }
    }
}
