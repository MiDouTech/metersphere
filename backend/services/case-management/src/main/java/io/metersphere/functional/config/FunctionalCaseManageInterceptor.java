package io.metersphere.functional.config;

import io.metersphere.functional.dto.FunctionalCaseMindDTO;
import io.metersphere.sdk.util.CompressUtils;
import io.metersphere.sdk.util.FunctionalCaseBlobCodec;
import io.metersphere.system.utils.MybatisInterceptorConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class FunctionalCaseManageInterceptor {
    @Bean
    public List<MybatisInterceptorConfig> functionalCaseManageCompressConfigs() {
        List<MybatisInterceptorConfig> configList = new ArrayList<>();

        configList.add(new MybatisInterceptorConfig(FunctionalCaseMindDTO.class, "steps", FunctionalCaseBlobCodec.class, "pack", "unpack"));
        configList.add(new MybatisInterceptorConfig(FunctionalCaseMindDTO.class, "executeSteps", CompressUtils.class, "zip", "unzip"));
        configList.add(new MybatisInterceptorConfig(FunctionalCaseMindDTO.class, "textDescription", FunctionalCaseBlobCodec.class, "pack", "unpack"));
        configList.add(new MybatisInterceptorConfig(FunctionalCaseMindDTO.class, "expectedResult", FunctionalCaseBlobCodec.class, "pack", "unpack"));
        configList.add(new MybatisInterceptorConfig(FunctionalCaseMindDTO.class, "prerequisite", FunctionalCaseBlobCodec.class, "pack", "unpack"));
        configList.add(new MybatisInterceptorConfig(FunctionalCaseMindDTO.class, "description", FunctionalCaseBlobCodec.class, "pack", "unpack"));
        configList.add(new MybatisInterceptorConfig(FunctionalCaseMindDTO.class, "content", CompressUtils.class, "zip", "unzip"));


        return configList;
    }
}
