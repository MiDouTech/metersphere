package io.metersphere.system.uid;

import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.system.uid.impl.DefaultUidGenerator;

public class IDGenerator {
    private static DefaultUidGenerator generator() {
        // Loading this utility before Spring is ready must not permanently cache null.
        DefaultUidGenerator generator = CommonBeanFactory.getBean(DefaultUidGenerator.class);
        if (generator == null) {
            throw new IllegalStateException("UID generator is not initialized");
        }
        return generator;
    }

    /**
     * 生成一个唯一的数字
     */
    public static Long nextNum() {
        return generator().getUID();
    }

    /**
     * 生成一个唯一的字符串
     */
    public static String nextStr() {
        return String.valueOf(generator().getUID());
    }
}
