package io.metersphere.system.dto.sso.miduo;

import lombok.Data;

import java.io.Serializable;

@Data
public class MiduoSsoStatusDTO implements Serializable {
    private boolean enabled;
    private boolean ready;
    private boolean localLoginEnabled;
    /** /#/login/admin 入口是否开启（system_parameter ui.login.admin.enabled，默认 false） */
    private boolean adminLoginEnabled;
    private String reason;
    private String message;
}
