import io.metersphere.agent.service.AgentExternalImageDownloader;
import io.metersphere.sdk.exception.MSException;
import java.util.concurrent.TimeUnit;

/** Real packaged downloader; no Spring application, database, mocked HTTP or Docker required. */
public final class ExternalImageRuntimeSmoke {
    private static int checks;
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void rejected(AgentExternalImageDownloader downloader, String name, String url, String code) {
        long start = System.nanoTime();
        try {
            downloader.download(url, name);
            throw new AssertionError(name + " unexpectedly accepted");
        } catch (MSException error) {
            require(code.equals(error.getMessage()), name + " expected " + code + " but got " + error.getMessage());
            checks++;
            System.out.printf("PASS %s code=%s elapsedMs=%d%n", name, code,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }
    public static void main(String[] args) {
            var downloader = new AgentExternalImageDownloader();
            try {
                var jpeg = downloader.download("https://httpbin.org/image/jpeg", "external-runtime");
                require("image/jpeg".equals(jpeg.contentType()) && jpeg.bytes().length > 0
                        && "external-runtime.jpg".equals(jpeg.fileName()), "JPEG result invalid");
                checks++;
                System.out.printf("PASS public-jpeg sizeBytes=%d%n", jpeg.bytes().length);
                rejected(downloader, "redirect-private", "https://httpbin.org/redirect-to?url=http%3A%2F%2F127.0.0.1%2Fimage.png", "EXTERNAL_IMAGE_TARGET_FORBIDDEN");
                rejected(downloader, "svg-source", "https://httpbin.org/image/svg", "EXTERNAL_IMAGE_TYPE_UNSUPPORTED");
                rejected(downloader, "non-image", "https://httpbin.org/html", "EXTERNAL_IMAGE_TYPE_UNSUPPORTED");
                rejected(downloader, "http-404", "https://httpbin.org/status/404", "EXTERNAL_IMAGE_FETCH_FAILED");
                rejected(downloader, "metadata-target", "http://169.254.169.254/latest/meta-data/", "EXTERNAL_IMAGE_TARGET_FORBIDDEN");
                rejected(downloader, "credential-source", "https://name:password@httpbin.org/image/jpeg", "EXTERNAL_IMAGE_URL_INVALID");
                rejected(downloader, "forbidden-port", "https://httpbin.org:8443/image/jpeg", "EXTERNAL_IMAGE_URL_INVALID");
                rejected(downloader, "slow-source", "https://httpbin.org/delay/20", "EXTERNAL_IMAGE_TIMEOUT");
                require(checks == 9, "Missing smoke checks");
                System.out.printf("All %d packaged downloader checks passed.%n", checks);
            } finally {
                downloader.close();
            }
    }
}
