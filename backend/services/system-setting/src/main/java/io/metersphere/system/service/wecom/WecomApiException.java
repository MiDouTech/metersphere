package io.metersphere.system.service.wecom;

import lombok.Getter;

@Getter
public class WecomApiException extends RuntimeException {

    private final int errcode;

    public WecomApiException(int errcode, String errmsg) {
        super("WeCom API error: errcode=" + errcode + ", errmsg=" + errmsg);
        this.errcode = errcode;
    }

    public boolean isTokenExpired() {
        return errcode == 42001 || errcode == 40014 || errcode == 41001;
    }
}
