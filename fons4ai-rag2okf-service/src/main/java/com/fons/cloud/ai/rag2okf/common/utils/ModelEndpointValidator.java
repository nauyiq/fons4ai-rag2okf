package com.fons.cloud.ai.rag2okf.common.utils;

import com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * 用户自定义模型端点校验工具，限制协议、端口和可访问地址范围。
 *
 * @author hongqy
 */
public final class ModelEndpointValidator {

    private ModelEndpointValidator() {
    }

    /**
     * 校验模型 API 根地址，拒绝非 HTTPS、非标准端口和本地网络地址。
     *
     * @param baseUrl 用户提供的模型 API 根地址
     */
    public static void validate(String baseUrl) {
        try {
            URI endpoint = new URI(baseUrl);
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                    || (endpoint.getPort() != -1 && endpoint.getPort() != 443)) {
                throw new ModelConfigurationException();
            }
            for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                if (isForbiddenAddress(address)) {
                    throw new ModelConfigurationException();
                }
            }
        } catch (URISyntaxException | UnknownHostException exception) {
            throw new ModelConfigurationException(exception);
        }
    }

    private static boolean isForbiddenAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address && (bytes[0] & 0xfe) == 0xfc) {
            return true;
        }
        if (bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return (first == 100 && second >= 64 && second <= 127)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 192 && second == 0);
    }
}
