package io.metersphere.functional.service;

public interface AiDocumentVirusScanner {
    void scan(String fileName, byte[] content);
}
