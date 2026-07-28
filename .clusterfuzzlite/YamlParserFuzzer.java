import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import io.skodjob.kubetest4j.utils.KubeTestUtils;

import java.util.Map;

public class YamlParserFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String yaml = data.consumeRemainingAsString();
        try {
            KubeTestUtils.configFromYaml(yaml, Map.class);
        } catch (IllegalArgumentException | RuntimeException e) {
            // Expected for malformed YAML
        }
    }
}
