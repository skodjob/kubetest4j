/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.environment;

import io.skodjob.kubetest4j.annotations.TestVisualSeparator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestVisualSeparator
class TestEnvironmentVariablesTest {

    @Test
    void testGetEnvVariablesCorrectly() {
        assertEquals(MyEnvs.MY_ENV, "this");
        assertEquals(MyEnvs.SECOND_ENV, "23");
        assertTrue(Files.exists(Paths.get(System.getProperty("user.dir"))
            .resolve("target").resolve("config.yaml")));
    }

    @Test
    void testKubernetesContextLoad() {
        assertEquals(3, MyEnvs.CLUSTER_CONFIGS.size());
        assertNotNull(MyEnvs.CLUSTER_CONFIGS.get("primary"));
        assertNotNull(MyEnvs.CLUSTER_CONFIGS.get("prod"));
        assertNotNull(MyEnvs.CLUSTER_CONFIGS.get("stage"));
    }

    @Test
    void testSystemPropertyFallback() {
        Map<String, String> envMap = Map.of("ENV_ONLY", "from-env");
        Map<String, String> sysProps = Map.of("SYS_ONLY", "from-sysprop");
        TestEnvironmentVariables vars = new TestEnvironmentVariables(envMap, sysProps);

        assertEquals("from-env", vars.getOrDefault("ENV_ONLY", "default"));
        assertEquals("from-sysprop", vars.getOrDefault("SYS_ONLY", "default"));
        assertEquals("default", vars.getOrDefault("MISSING", "default"));
    }

    @Test
    void testEnvVarTakesPrecedenceOverSystemProperty() {
        Map<String, String> envMap = Map.of("SHARED_KEY", "from-env");
        Map<String, String> sysProps = Map.of("SHARED_KEY", "from-sysprop");
        TestEnvironmentVariables vars = new TestEnvironmentVariables(envMap, sysProps);

        assertEquals("from-env", vars.getOrDefault("SHARED_KEY", "default"));
    }

    @Test
    void testSystemPropertyTakesPrecedenceOverDefault() {
        Map<String, String> sysProps = Map.of("MY_PROP", "from-sysprop");
        TestEnvironmentVariables vars =
            new TestEnvironmentVariables(Collections.emptyMap(), sysProps);

        assertEquals("from-sysprop", vars.getOrDefault("MY_PROP", "default"));
    }

    @Test
    void testDiscoverClusterConfigsFromSystemProperties() {
        Map<String, String> sysProps = Map.of(
            "KUBE_URL_DEV", "https://dev.example.com:6443",
            "KUBE_TOKEN_DEV", "dev-token"
        );
        TestEnvironmentVariables vars =
            new TestEnvironmentVariables(Collections.emptyMap(), sysProps);

        Map<String, TestEnvironmentVariables.ClusterConfig> configs =
            vars.discoverClusterConfigs();

        assertNotNull(configs.get("primary"));
        assertNotNull(configs.get("dev"));
        assertEquals("https://dev.example.com:6443", configs.get("dev").url());
        assertEquals("dev-token", configs.get("dev").token());
        assertNull(configs.get("dev").kubeconfigPath());
    }

    public static class MyEnvs {
        private static final Map<String, String> ENVS_MAP = Map.of(
            "MY_ENV", "this",
            "THIRD_ENV", "that",
            "KUBECONFIG", "/user/home/kornys.config",
            "KUBECONFIG_PROD", "/user/home/kornys.config",
            "KUBE_URL_STAGE", "https://pepa.com:6443",
            "KUBE_TOKEN_STAGE", "TOKEN"
        );

        public static final TestEnvironmentVariables ENVIRONMENT_VARIABLES = new TestEnvironmentVariables(ENVS_MAP);
        public static final String MY_ENV = ENVIRONMENT_VARIABLES.getOrDefault("MY_ENV", "");
        public static final String SECOND_ENV = ENVIRONMENT_VARIABLES.getOrDefault("SECOND_ENV", "23");
        public static final Map<String, TestEnvironmentVariables.ClusterConfig> CLUSTER_CONFIGS =
            ENVIRONMENT_VARIABLES.discoverClusterConfigs();

        static {
            ENVIRONMENT_VARIABLES.logEnvironmentVariables();
            try {
                ENVIRONMENT_VARIABLES.saveConfigurationFile(Paths.get(System.getProperty("user.dir"))
                    .resolve("target").toAbsolutePath().toString());
            } catch (IOException e) {
                fail("Env vars not saved");
            }
        }
    }
}
