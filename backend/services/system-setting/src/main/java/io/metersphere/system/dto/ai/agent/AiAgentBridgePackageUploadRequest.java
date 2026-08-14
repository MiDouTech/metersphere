package io.metersphere.system.dto.ai.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAgentBridgePackageUploadRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$", message = "版本号格式不正确")
    private String version;

    @NotBlank
    @Pattern(regexp = "WINDOWS|MACOS|LINUX", message = "不支持的操作系统")
    private String osType;

    @NotBlank
    @Pattern(regexp = "X64|ARM64", message = "不支持的处理器架构")
    private String architecture;

    @Size(max = 1000)
    private String description;

    private Boolean activate;
}
