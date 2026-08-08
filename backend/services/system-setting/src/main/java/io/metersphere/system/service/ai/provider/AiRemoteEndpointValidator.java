package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.URI;

final class AiRemoteEndpointValidator {
    private AiRemoteEndpointValidator() {
    }

    static void validateHttps(String value, String label, boolean allowPrivateAddresses) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception ex) {
            throw new MSException(label + " URL 非法");
        }
        String host = StringUtils.lowerCase(uri.getHost());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || StringUtils.isBlank(host)
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new MSException(label + " 必须使用无用户信息的 HTTPS 地址");
        }
        if ("metadata.google.internal".equals(host) || "metadata.azure.internal".equals(host)) {
            throw new MSException(label + " 不允许访问云元数据地址");
        }
        if (allowPrivateAddresses) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new MSException(label + " 不允许访问本地或私有网络地址");
                }
            }
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException(label + " 主机无法安全解析");
        }
    }
}
