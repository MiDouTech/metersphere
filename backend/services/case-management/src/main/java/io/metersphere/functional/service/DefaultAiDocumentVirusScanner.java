package io.metersphere.functional.service;

import io.metersphere.sdk.exception.MSException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class DefaultAiDocumentVirusScanner implements AiDocumentVirusScanner {
    private static final String EICAR_MARKER = "EICAR-STANDARD-ANTIVIRUS-TEST-FILE";

    @Override
    public void scan(String fileName, byte[] content) {
        // Stable extension point for an enterprise ClamAV/ICAP adapter. The default
        // implementation rejects the industry-standard EICAR test signature so the
        // upload path cannot silently claim that scanning was performed.
        String searchable = new String(content, StandardCharsets.ISO_8859_1);
        if (searchable.contains(EICAR_MARKER)) {
            throw new MSException("文件安全扫描未通过");
        }
    }
}
