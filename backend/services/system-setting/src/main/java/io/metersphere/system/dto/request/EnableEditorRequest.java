package io.metersphere.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class EnableEditorRequest implements Serializable {
    @Schema(description = "是否开启")
    private Boolean enable;
}
