package io.metersphere.system.dto.sso.miduo;

import lombok.Data;

import java.io.Serializable;

@Data
public class MiduoSsoStatusDTO implements Serializable {
    private boolean enabled;
    private boolean ready;
    private boolean localLoginEnabled;
    private String reason;
    private String message;
}
