package cn.ksuser.api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class IpLocationService {

    private static final Logger logger = LoggerFactory.getLogger(IpLocationService.class);
    private static final String IP_LOCATION_API = "https://whois.pconline.com.cn/ipJson.jsp?ip=%s&json=true";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public IpLocationService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取IP属地
     * @param ip IP地址
     * @return IP属地字符串，如"广东省深圳市"，获取失败返回null
     */
    public String getIpLocation(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }

        // 跳过本地/内网IP
        if (isLocalOrPrivateIp(ip)) {
            return "内网IP";
        }

        try {
            String url = String.format(IP_LOCATION_API, URLEncoder.encode(ip, StandardCharsets.UTF_8));
            String response = restTemplate.getForObject(url, String.class);
            
            if (response != null && !response.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode addrNode = jsonNode.get("addr");
                if (addrNode != null && !addrNode.isNull()) {
                    return addrNode.asText();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get IP location for {}: {}", ip, e.getMessage());
        }

        return null;
    }

    /**
     * 判断是否为本地或内网IP
     */
    private boolean isLocalOrPrivateIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }

        // 本地回环地址
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return true;
        }

        // IPv4私有地址
        if (ip.startsWith("10.") || 
            ip.startsWith("192.168.") || 
            (ip.startsWith("172.") && isInRange(ip, 16, 31))) {
            return true;
        }

        return false;
    }

    private boolean isInRange(String ip, int start, int end) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= start && second <= end;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
}
