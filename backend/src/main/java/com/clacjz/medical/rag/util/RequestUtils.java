package com.clacjz.medical.rag.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RequestUtils {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::$|^([0-9a-fA-F]{1,4}:)*:[0-9a-fA-F]{1,4}$"
    );

    @Value("${app.security.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Value("${app.security.trusted-proxies:}")
    private List<String> trustedProxies;

    public HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    public String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "unknown";
        }

        String remoteAddr = request.getRemoteAddr();

        if (!trustProxyHeaders) {
            return remoteAddr;
        }

        // 检查 X-Forwarded-For
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(forwardedFor)) {
            String clientIp = forwardedFor.split(",")[0].trim();
            if (isValidIp(clientIp)) {
                if (!isIpInTrustedList(remoteAddr)) {
                    return remoteAddr;
                }
                return clientIp;
            }
        }

        // 检查 X-Real-IP
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            if (isValidIp(realIp)) {
                if (!isIpInTrustedList(remoteAddr)) {
                    return remoteAddr;
                }
                return realIp;
            }
        }

        // 检查 Proxy-Client-IP
        String proxyClientIp = request.getHeader("Proxy-Client-IP");
        if (proxyClientIp != null && !proxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(proxyClientIp)) {
            if (isValidIp(proxyClientIp)) {
                return proxyClientIp;
            }
        }

        // 检查 WL-Proxy-Client-IP
        String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
        if (wlProxyClientIp != null && !wlProxyClientIp.isEmpty() && !"unknown".equalsIgnoreCase(wlProxyClientIp)) {
            if (isValidIp(wlProxyClientIp)) {
                return wlProxyClientIp;
            }
        }

        // 检查 HTTP_X_FORWARDED_FOR
        String httpXForwardedFor = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (httpXForwardedFor != null && !httpXForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(httpXForwardedFor)) {
            String clientIp = httpXForwardedFor.split(",")[0].trim();
            if (isValidIp(clientIp)) {
                return clientIp;
            }
        }

        // 检查 HTTP_X_FORWARDED
        String httpXForwarded = request.getHeader("HTTP_X_FORWARDED");
        if (httpXForwarded != null && !httpXForwarded.isEmpty() && !"unknown".equalsIgnoreCase(httpXForwarded)) {
            if (isValidIp(httpXForwarded)) {
                return httpXForwarded;
            }
        }

        // 检查 HTTP_X_CLUSTER_CLIENT_IP
        String httpXClusterClientIp = request.getHeader("HTTP_X_CLUSTER_CLIENT_IP");
        if (httpXClusterClientIp != null && !httpXClusterClientIp.isEmpty() && !"unknown".equalsIgnoreCase(httpXClusterClientIp)) {
            if (isValidIp(httpXClusterClientIp)) {
                return httpXClusterClientIp;
            }
        }

        // 检查 HTTP_CLIENT_IP
        String httpClientIp = request.getHeader("HTTP_CLIENT_IP");
        if (httpClientIp != null && !httpClientIp.isEmpty() && !"unknown".equalsIgnoreCase(httpClientIp)) {
            if (isValidIp(httpClientIp)) {
                return httpClientIp;
            }
        }

        // 检查 HTTP_FORWARDED_FOR
        String httpForwardedFor = request.getHeader("HTTP_FORWARDED_FOR");
        if (httpForwardedFor != null && !httpForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(httpForwardedFor)) {
            if (isValidIp(httpForwardedFor)) {
                return httpForwardedFor;
            }
        }

        // 检查 HTTP_FORWARDED
        String httpForwarded = request.getHeader("HTTP_FORWARDED");
        if (httpForwarded != null && !httpForwarded.isEmpty() && !"unknown".equalsIgnoreCase(httpForwarded)) {
            if (isValidIp(httpForwarded)) {
                return httpForwarded;
            }
        }

        // 检查 HTTP_VIA
        String httpVia = request.getHeader("HTTP_VIA");
        if (httpVia != null && !httpVia.isEmpty() && !"unknown".equalsIgnoreCase(httpVia)) {
            if (isValidIp(httpVia)) {
                return httpVia;
            }
        }

        // 检查 REMOTE_ADDR
        String remoteAddrHeader = request.getHeader("REMOTE_ADDR");
        if (remoteAddrHeader != null && !remoteAddrHeader.isEmpty() && !"unknown".equalsIgnoreCase(remoteAddrHeader)) {
            if (isValidIp(remoteAddrHeader)) {
                return remoteAddrHeader;
            }
        }

        return remoteAddr;
    }

    public String getUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "unknown";
        }
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // 去除 IPv6 完整格式的前缀（::ffff:）
        if (ip.toLowerCase().startsWith("::ffff:")) {
            ip = ip.substring(7);
        }
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }

    private boolean isIpInTrustedList(String ip) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return true;
        }
        for (String trusted : trustedProxies) {
            if (trusted.contains("/")) {
                // CIDR 格式，如 172.16.0.0/12
                if (isIpInCidr(ip, trusted)) {
                    return true;
                }
            } else if (trusted.equals(ip)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String networkAddress = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            InetAddress addr = InetAddress.getByName(ip);
            InetAddress network = InetAddress.getByName(networkAddress);

            byte[] ipBytes = addr.getAddress();
            byte[] networkBytes = network.getAddress();

            if (ipBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < ipBytes.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((ipBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
