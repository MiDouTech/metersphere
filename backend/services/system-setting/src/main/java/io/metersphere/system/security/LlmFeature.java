package io.metersphere.system.security;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.CommonResultCode;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LlmFeature {
    @Value("${features.llm-ui-enabled:false}")
    private boolean enabled;

    public boolean isEnabled() { return enabled; }

    @Before("@annotation(io.metersphere.system.security.RequiresLlmFeature)")
    public void requireEnabled() {
        if (!enabled) throw new MSException(CommonResultCode.FEATURE_DISABLED, "平台模型功能未启用，请返回使用其他功能");
    }
}
