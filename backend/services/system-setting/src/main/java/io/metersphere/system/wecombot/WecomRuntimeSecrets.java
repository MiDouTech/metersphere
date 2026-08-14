package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WecomRuntimeSecrets {
    private WecomRuntimeSecrets() {
    }

    public static String read(String name) {
        String value = System.getenv(name);
        if (StringUtils.isNotBlank(value)) return value;
        String file = System.getenv(name + "_FILE");
        if (StringUtils.isBlank(file)) return null;
        try {
            return Files.readString(Path.of(file)).trim();
        } catch (Exception e) {
            throw new MSException("Unable to read configured WeCom runtime secret file");
        }
    }
}
