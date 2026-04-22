package cn.ksuser.api.util;

import java.util.Arrays;
import java.util.List;

/**
 * IP工具类，用于IP地址验证和CIDR网段匹配
 */
public class IpUtil {

    /**
     * 可信的代理IP CIDR网段列表（内网地址和本地环回地址）
     */
    private static final List<String> TRUSTED_PROXY_CIDRS = Arrays.asList(
        "127.0.0.1/32",           // 本地环回地址
        "::1/128",                 // IPv6本地环回地址
        "10.0.0.0/8",             // 私网地址
        "172.16.0.0/12",          // 私网地址
        "192.168.0.0/16",         // 私网地址
        "169.254.0.0/16",         // 链接本地地址
        "fc00::/7",               // IPv6私网地址
        "fe80::/10"               // IPv6链接本地地址
    );

    /**
     * 判断IP是否在CIDR网段内
     * @param ip 要检查的IP地址
     * @param cidr CIDR表示法的网段（如 "192.168.1.0/24"）
     * @return 如果IP在网段内返回true，否则返回false
     */
    public static boolean isCidrMatch(String ip, String cidr) {
        try {
            // 解析CIDR表示法
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }

            String networkAddress = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            // 判断是IPv4还是IPv6
            if (ip.contains(":") || networkAddress.contains(":")) {
                return isCidrMatchIPv6(ip, networkAddress, prefixLength);
            } else {
                return isCidrMatchIPv4(ip, networkAddress, prefixLength);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * IPv4地址的CIDR匹配
     * @param ip 要检查的IP地址
     * @param networkAddress 网络地址
     * @param prefixLength 前缀长度
     * @return 是否匹配
     */
    private static boolean isCidrMatchIPv4(String ip, String networkAddress, int prefixLength) {
        try {
            long ipNum = ipv4ToLong(ip);
            long networkNum = ipv4ToLong(networkAddress);

            // 计算掩码
            long mask = -1L << (32 - prefixLength);

            return (ipNum & mask) == (networkNum & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * IPv6地址的CIDR匹配
     * @param ip 要检查的IP地址
     * @param networkAddress 网络地址
     * @param prefixLength 前缀长度
     * @return 是否匹配
     */
    private static boolean isCidrMatchIPv6(String ip, String networkAddress, int prefixLength) {
        try {
            byte[] ipBytes = ipv6ToBytes(ip);
            byte[] networkBytes = ipv6ToBytes(networkAddress);

            if (ipBytes == null || networkBytes == null) {
                return false;
            }

            // 比较前缀
            int byteIndex = 0;
            int bitIndex = 0;

            while (bitIndex < prefixLength && byteIndex < 16) {
                int bitsToCheck = Math.min(8, prefixLength - bitIndex);
                int mask = (0xFF << (8 - bitsToCheck)) & 0xFF;

                if ((ipBytes[byteIndex] & mask) != (networkBytes[byteIndex] & mask)) {
                    return false;
                }

                byteIndex++;
                bitIndex += 8;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将IPv4地址转换为长整数
     * @param ip IPv4地址字符串
     * @return 长整数表示
     */
    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + octet);
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    /**
     * 将IPv6地址转换为字节数组
     * @param ip IPv6地址字符串
     * @return 字节数组表示
     */
    private static byte[] ipv6ToBytes(String ip) {
        try {
            return java.net.InetAddress.getByName(ip).getAddress();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断IP是否是可信的代理IP（内网地址或代理地址）
     * @param ip 要检查的IP地址
     * @return 如果是可信代理返回true，否则返回false
     */
    public static boolean isTrustedProxyIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // 检查IP是否在可信CIDR网段内
        for (String cidr : TRUSTED_PROXY_CIDRS) {
            if (isCidrMatch(ip, cidr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证IP地址格式是否正确
     * @param ip IP地址字符串
     * @return 如果是有效的IP地址返回true
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // IPv4验证
        if (ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            String[] parts = ip.split("\\.");
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        }

        // IPv6验证（简单检查）
        if (ip.contains(":")) {
            try {
                java.net.InetAddress.getByName(ip);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * 获取客户端真实IP地址
     * 考虑代理和负载均衡的情况
     * @param request HTTP请求对象
     * @return 客户端IP地址
     */
    public static String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        // 按优先级检查各种可能包含真实IP的请求头
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For可能包含多个IP，取第一个
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIp(ip)) {
                    return ip;
                }
            }
        }

        // 如果所有头部都没有，使用remoteAddr
        String ip = request.getRemoteAddr();
        return (ip != null && !ip.isEmpty()) ? ip : "unknown";
    }
}
