/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.helpers;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

public class TestListener implements TestExecutionExceptionHandler {

    private TestListener() {
        // Private constructor to prevent instantiation
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (!context.getDisplayName().contains("testFailAnCallMustGather")) {
            throw throwable;
        }
    }
}
