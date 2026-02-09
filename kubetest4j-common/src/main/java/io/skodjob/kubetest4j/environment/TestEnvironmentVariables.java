/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.environment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.skodjob.kubetest4j.TestFrameConstants;
import io.skodjob.kubetest4j.utils.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Class representing environment variables used in the test suite
 */
public class TestEnvironmentVariables {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEnvironmentVariables.class);

    /* test */ private final Map<String, String> envMap;
    private final Map<String, String> values = new HashMap<>();
    private Map<String, Object> yamlData = new HashMap<>();
    private final String configFilePath;
    private static final String CONFIG_FILE_PATH_ENV = "ENV_FILE";

    /**
     * {@link TestEnvironmentVariables} object initialization, where the config file is loaded to {@link #yamlData}
     * if possible.
     */
    public TestEnvironmentVariables() {
        this(System.getenv());
    }

    /**
     * {@link TestEnvironmentVariables} object initialization, where the config file is loaded to {@link #yamlData}
     * if possible.
     *
     * @param envMap Map containing the environment variables. Used mainly for testing purposes.
     */
    public TestEnvironmentVariables(Map<String, String> envMap) {
        this.envMap = envMap;
        this.configFilePath = envMap.getOrDefault(CONFIG_FILE_PATH_ENV,
            Paths.get(System.getProperty("user.dir"), "config.yaml").toAbsolutePath().toString());
        this.yamlData = loadConfigurationFile();
    }

    /**
     * Method which returns the value from env variable or its default in String.
     *
     * @param envVarName   environment variable name
     * @param defaultValue default value, which should be used if the env var is empty and the config file
     *                     doesn't contain it
     * @return value from env var/config file or default
     */
    public String getOrDefault(String envVarName, String defaultValue) {
        return getOrDefault(envVarName, String::toString, defaultValue);
    }

    /**
     * Method which returns the value from env variable or its default in the specified type.
     * It also checks if the env var is specified in the configuration file before applying the default.
     *
     * @param envVarName   environment variable name
     * @param converter    converter to the desired type
     * @param defaultValue default value, which should be used if the env var is empty and the config file
     *                     doesn't contain it
     * @param <T>          desired type
     * @return value from env var/config file or default
     */
    public <T> T getOrDefault(String envVarName, Function<String, T> converter, T defaultValue) {
        String value = envMap.get(envVarName) != null ?
            envMap.get(envVarName) :
            (Objects.requireNonNull(yamlData).get(envVarName) != null ?
                yamlData.get(envVarName).toString() :
                null);

        T returnValue = defaultValue;

        if (value != null) {
            returnValue = converter.apply(value);
        }

        values.put(envVarName, String.valueOf(returnValue));
        return returnValue;
    }

    /**
     * Method that loads configuration file - either from specified path by {@link #CONFIG_FILE_PATH_ENV} or from
     * the default path (in the `config.yaml` file on the `user.dir` path).
     * If the file doesn't exist, the info log is printed and empty Map is returned.
     *
     * @return Map with env variables and their values, or empty Map in case of not existing file
     */
    protected Map<String, Object> loadConfigurationFile() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            File yamlFile = new File(configFilePath).getAbsoluteFile();
            return mapper.readValue(new File(yamlFile.getAbsoluteFile().toString()),
                new TypeReference<HashMap<String, Object>>() {
                });
        } catch (IOException ex) {
            LOGGER.info("Yaml configuration not provider or not exists");
            return Collections.emptyMap();
        }
    }

    /***
     * Saves all set environment variables into yaml file
     *
     * @param testLogDir dir where to store file with set env vars
     * @throws IOException ioException
     */
    public void saveConfigurationFile(String testLogDir) throws IOException {
        Path logPath = Path.of(testLogDir);
        Files.createDirectories(logPath);

        LinkedHashMap<String, String> toSave = new LinkedHashMap<>(values);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writerWithDefaultPrettyPrinter().writeValue(logPath.resolve("config.yaml").toFile(), toSave);
    }

    /**
     * Method which logs environment variables parsed by {@link TestEnvironmentVariables}
     */
    public void logEnvironmentVariables() {
        String debugFormat = "{}: {}";

        LoggerUtils.logSeparator("-", 30);

        LOGGER.info("Used environment variables:");

        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (!Objects.equals(entry.getValue(), "null")) {
                    LOGGER.info(debugFormat, entry.getKey(), entry.getValue());
                }
            });

        LoggerUtils.logSeparator("-", 30);
    }

    /**
     * Container for kubernetes connection
     *
     * @param url            api url
     * @param token          api token
     * @param kubeconfigPath kubeconfig
     */
    public record ClusterConfig(String url, String token, String kubeconfigPath) {
    }

    /**
     * Load cluster configs
     *
     * @return map of cluster configs
     */
    public Map<String, ClusterConfig> discoverClusterConfigs() {
        Map<String, ClusterConfig> out = new HashMap<>();

        /* ---------- default context ---------- */
        String defKC = getOrDefault("KUBECONFIG", null);
        String defURL = getOrDefault("KUBE_URL", null);
        String defTok = getOrDefault("KUBE_TOKEN", null);

        if (defKC != null) {
            out.put(TestFrameConstants.DEFAULT_CONTEXT_NAME,
                new ClusterConfig(null, null, defKC));
        } else if (defURL != null && defTok != null) {
            out.put(TestFrameConstants.DEFAULT_CONTEXT_NAME,
                new ClusterConfig(defURL, defTok, null));
        } else {
            out.put(TestFrameConstants.DEFAULT_CONTEXT_NAME,
                new ClusterConfig(null, null, null));
        }

        /* ---------- suffixed contexts ---------- */
        var keySource = new HashSet<>(envMap.keySet());
        if (yamlData != null) keySource.addAll(yamlData.keySet());

        keySource.stream()
            .filter(k -> k.startsWith("KUBECONFIG_") || k.startsWith("KUBE_URL_") || k.startsWith("KUBE_TOKEN_"))
            .map(k -> k.substring(k.lastIndexOf('_') + 1).toLowerCase())
            .distinct()
            .forEach(id -> {
                String kc = getOrDefault("KUBECONFIG_" + id.toUpperCase(), null);
                String url = getOrDefault("KUBE_URL_" + id.toUpperCase(), null);
                String tok = getOrDefault("KUBE_TOKEN_" + id.toUpperCase(), null);

                if (kc != null) {
                    out.put(id, new ClusterConfig(null, null, kc));
                } else if (url != null && tok != null) {
                    out.put(id, new ClusterConfig(url, tok, null));
                }
            });

        return java.util.Collections.unmodifiableMap(out);
    }
}
