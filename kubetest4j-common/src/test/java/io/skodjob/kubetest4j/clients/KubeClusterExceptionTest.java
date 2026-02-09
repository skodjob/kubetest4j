/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.clients;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import io.skodjob.kubetest4j.executor.ExecResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestVisualSeparator
class KubeClusterExceptionTest {

    @Test
    void testKubeClusterExceptionWithExecResult() {
        ExecResult mockResult = mock(ExecResult.class);
        when(mockResult.returnCode()).thenReturn(1);
        when(mockResult.out()).thenReturn("Test output");

        String message = "Test exception message";
        KubeClusterException exception = new KubeClusterException(mockResult, message);

        assertEquals(message, exception.getMessage());
        assertEquals(mockResult, exception.result);
        assertNotNull(exception.result);
    }

    @Test
    void testKubeClusterExceptionWithThrowable() {
        RuntimeException cause = new RuntimeException("Root cause");
        KubeClusterException exception = new KubeClusterException(cause);

        assertEquals(cause, exception.getCause());
        assertNull(exception.result);
    }

    @Test
    void testNotFoundExceptionExtension() {
        ExecResult mockResult = mock(ExecResult.class);
        when(mockResult.returnCode()).thenReturn(404);
        when(mockResult.err()).thenReturn("Resource not found");

        String message = "Pod not found";
        KubeClusterException.NotFound notFoundException =
            new KubeClusterException.NotFound(mockResult, message);

        assertEquals(message, notFoundException.getMessage());
        assertEquals(mockResult, notFoundException.result);
        assertEquals(404, notFoundException.result.returnCode());
        assertEquals("Resource not found", notFoundException.result.err());

        // Verify inheritance
        assertInstanceOf(KubeClusterException.class, notFoundException);
        assertInstanceOf(RuntimeException.class, notFoundException);
    }

    @Test
    void testAlreadyExistsExceptionExtension() {
        ExecResult mockResult = mock(ExecResult.class);
        when(mockResult.returnCode()).thenReturn(409);
        when(mockResult.err()).thenReturn("Resource already exists");

        String message = "Namespace already exists";
        KubeClusterException.AlreadyExists alreadyExistsException =
            new KubeClusterException.AlreadyExists(mockResult, message);

        assertEquals(message, alreadyExistsException.getMessage());
        assertEquals(mockResult, alreadyExistsException.result);
        assertEquals(409, alreadyExistsException.result.returnCode());
        assertEquals("Resource already exists", alreadyExistsException.result.err());

        // Verify inheritance
        assertInstanceOf(KubeClusterException.class, alreadyExistsException);
        assertInstanceOf(RuntimeException.class, alreadyExistsException);
    }

    @Test
    void testInvalidResourceExceptionExtension() {
        ExecResult mockResult = mock(ExecResult.class);
        when(mockResult.returnCode()).thenReturn(400);
        when(mockResult.err()).thenReturn("Invalid resource specification");

        String message = "Invalid YAML format";
        KubeClusterException.InvalidResource invalidResourceException =
            new KubeClusterException.InvalidResource(mockResult, message);

        assertEquals(message, invalidResourceException.getMessage());
        assertEquals(mockResult, invalidResourceException.result);
        assertEquals(400, invalidResourceException.result.returnCode());
        assertEquals("Invalid resource specification", invalidResourceException.result.err());

        // Verify inheritance
        assertInstanceOf(KubeClusterException.class, invalidResourceException);
        assertInstanceOf(RuntimeException.class, invalidResourceException);
    }

    @Test
    void testExceptionChaining() {
        // Test that these exceptions can be properly chained and caught
        ExecResult mockResult = mock(ExecResult.class);
        when(mockResult.returnCode()).thenReturn(500);

        try {
            throw new KubeClusterException.NotFound(mockResult, "Not found");
        } catch (KubeClusterException e) {
            assertEquals("Not found", e.getMessage());
            assertEquals(500, e.result.returnCode());
        } catch (RuntimeException e) {
            // Should not reach here
            assertEquals("Should catch as KubeClusterException", e.getMessage());
        }
    }

    @Test
    void testAllExceptionTypesCanBeInstantiated() {
        ExecResult mockResult = mock(ExecResult.class);

        // Test that all exception types can be created
        KubeClusterException base = new KubeClusterException(mockResult, "base");
        KubeClusterException.NotFound notFound = new KubeClusterException.NotFound(mockResult, "not found");
        KubeClusterException.AlreadyExists alreadyExists = new KubeClusterException.AlreadyExists(mockResult, "exists");
        KubeClusterException.InvalidResource invalidResource =
            new KubeClusterException.InvalidResource(mockResult, "invalid");

        assertNotNull(base);
        assertNotNull(notFound);
        assertNotNull(alreadyExists);
        assertNotNull(invalidResource);

        // Verify all have the same result reference
        assertEquals(mockResult, base.result);
        assertEquals(mockResult, notFound.result);
        assertEquals(mockResult, alreadyExists.result);
        assertEquals(mockResult, invalidResource.result);
    }
}