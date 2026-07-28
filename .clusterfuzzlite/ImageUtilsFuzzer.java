import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import io.skodjob.kubetest4j.utils.ImageUtils;

public class ImageUtilsFuzzer {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String image = data.consumeString(500);
        String newRegistry = data.consumeBoolean() ? data.consumeString(100) : null;
        String newOrg = data.consumeBoolean() ? data.consumeString(100) : null;
        String newImageName = data.consumeBoolean() ? data.consumeString(100) : null;
        String newTag = data.consumeBoolean() ? data.consumeRemainingAsString() : null;

        try {
            ImageUtils.changeRegistryOrgImageAndTag(image, newRegistry, newOrg, newImageName, newTag);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                return;
            }
            throw new RuntimeException(e);
        }
    }
}
