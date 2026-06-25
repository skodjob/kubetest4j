/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.skodjob.kubetest4j.interfaces.ResourceType;
import io.skodjob.kubetest4j.utils.LoggerUtils;

import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Handles resource update and replace operations.
 *
 * <p>Package-private — accessed only through {@link KubeResourceManager}.
 */
final class ResourceUpdateService {

    private final KubeResourceManager manager;

    ResourceUpdateService(KubeResourceManager manager) {
        this.manager = manager;
    }

    @SafeVarargs
    final <T extends HasMetadata> void updateResource(T... resources) {
        for (T resource : resources) {
            LoggerUtils.logResource("Updating", resource);
            ResourceType<T> type = manager.findResourceType(resource);
            if (type != null) {
                type.update(resource);
            } else {
                manager.kubeClient().getClient()
                    .resource(resource).update();
            }
        }
    }

    <T extends HasMetadata> void replaceResourceWithRetries(
        T resource, Consumer<T> editor) {
        replaceResourceWithRetries(resource, editor, 3);
    }

    <T extends HasMetadata> void replaceResourceWithRetries(
        T resource, Consumer<T> editor, int retries) {
        int attempt = 0;
        while (true) {
            try {
                replaceResource(resource, editor);
                return;
            } catch (CompletionException ce) {
                Throwable cause = ce.getCause();
                if (isNotConflict(cause) || attempt++ >= retries) {
                    throw (cause instanceof RuntimeException re)
                        ? re : new RuntimeException(cause);
                }
            } catch (KubernetesClientException kce) {
                if (isNotConflict(kce) || attempt++ >= retries) {
                    throw kce;
                }
            }
        }
    }

    <T extends HasMetadata> void replaceResource(
        T resource, Consumer<T> editor) {
        ResourceType<T> type = manager.findResourceType(resource);
        if (type != null) {
            type.replace(resource, editor);
        } else {
            T current = manager.kubeClient().getClient()
                .resource(resource).get();
            editor.accept(current);
            manager.kubeClient().getClient()
                .resource(current).update();
        }
    }

    static boolean isNotConflict(Throwable t) {
        return !(t instanceof KubernetesClientException kce
            && kce.getCode() == 409);
    }
}
