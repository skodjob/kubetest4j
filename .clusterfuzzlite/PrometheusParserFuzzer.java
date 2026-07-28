import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import io.skodjob.kubetest4j.metrics.PrometheusTextFormatParser;

public class PrometheusParserFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String input = data.consumeRemainingAsString();
        try {
            PrometheusTextFormatParser.parse(input);
        } catch (Exception e) {
            // IOException and NumberFormatException are expected for malformed input.
            // Any other unchecked exception (e.g., ArrayIndexOutOfBoundsException,
            // NullPointerException, StringIndexOutOfBoundsException) is a real bug.
            if (e instanceof java.io.IOException
                || e instanceof NumberFormatException) {
                return;
            }
            throw new RuntimeException(e);
        }
    }
}
