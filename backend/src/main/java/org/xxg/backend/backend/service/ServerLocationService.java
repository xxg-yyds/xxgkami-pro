package org.xxg.backend.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ServerLocationService {

    private static final int TIMEOUT_MS = 5000;
    private static final int GEO_TIMEOUT_SEC = 6;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Object> getServerLocation() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("checkedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        String localIp = resolveLocalIp();
        result.put("localIp", localIp);

        String publicIp = resolvePublicIp();
        result.put("publicIp", publicIp != null ? publicIp : localIp);

        String queryIp = publicIp != null ? publicIp : localIp;
        result.put("queryIp", queryIp);

        CompletableFuture<Map<String, Object>> domesticFuture =
                CompletableFuture.supplyAsync(() -> queryDomestic(queryIp));
        CompletableFuture<Map<String, Object>> internationalFuture =
                CompletableFuture.supplyAsync(() -> queryInternational(queryIp));
        try {
            result.put("domestic", domesticFuture.get(GEO_TIMEOUT_SEC, TimeUnit.SECONDS));
            result.put("international", internationalFuture.get(GEO_TIMEOUT_SEC, TimeUnit.SECONDS));
        } catch (Exception e) {
            result.put("domestic", domesticFuture.isDone()
                    ? domesticFuture.getNow(failedGeo("太平洋网络 IP 库", "国内查询超时"))
                    : failedGeo("太平洋网络 IP 库", "国内查询超时"));
            result.put("international", internationalFuture.isDone()
                    ? internationalFuture.getNow(failedGeo("ipwho.is", "国外查询超时"))
                    : failedGeo("ipwho.is", "国外查询超时"));
        }
        return result;
    }

    private Map<String, Object> failedGeo(String provider, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", provider);
        data.put("success", false);
        data.put("message", message);
        return data;
    }

    private String resolvePublicIp() {
        for (String url : List.of(
                "https://api.ipify.org",
                "https://ifconfig.me/ip",
                "https://icanhazip.com"
        )) {
            try {
                String ip = utf8RestTemplate().getForObject(url, String.class);
                if (ip != null) {
                    ip = ip.trim();
                    if (isValidIpv4(ip)) {
                        return ip;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String resolveLocalIp() {
        List<String> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        candidates.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        for (String ip : candidates) {
            if (!ip.startsWith("169.254.") && !ip.startsWith("127.")) {
                return ip;
            }
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> queryDomestic(String ip) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "太平洋网络 IP 库");
        if (ip == null || ip.isBlank()) {
            data.put("success", false);
            data.put("message", "无法获取 IP");
            return data;
        }
        try {
            RestTemplate rest = gbkRestTemplate();
            String url = "https://whois.pconline.com.cn/ipJson.jsp?ip=" + ip.trim() + "&json=true";
            String body = rest.getForObject(url, String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("国内接口无响应");
            }
            JsonNode node = MAPPER.readTree(body.trim());
            if (node.hasNonNull("err") && !node.get("err").asText("").isBlank()) {
                throw new IllegalStateException(node.get("err").asText());
            }
            String pro = textOrEmpty(node, "pro");
            String city = textOrEmpty(node, "city");
            String region = textOrEmpty(node, "region");
            String addr = textOrEmpty(node, "addr");
            String location = !addr.isBlank() ? addr : joinParts(pro, city, region);
            data.put("success", true);
            data.put("location", location.isBlank() ? "未知位置" : location);
            data.put("province", pro);
            data.put("city", city);
            data.put("region", region);
            data.put("address", addr);
            data.put("isp", extractIspFromAddr(addr));
        } catch (Exception e) {
            data.put("success", false);
            data.put("message", e.getMessage() != null ? e.getMessage() : "国内查询失败");
        }
        return data;
    }

    private Map<String, Object> queryInternational(String ip) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "ipwho.is");
        if (ip == null || ip.isBlank()) {
            data.put("success", false);
            data.put("message", "无法获取 IP");
            return data;
        }
        try {
            String url = "https://ipwho.is/" + ip.trim();
            String body = utf8RestTemplate().getForObject(url, String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("国外接口无响应");
            }
            JsonNode node = MAPPER.readTree(body);
            if (!node.path("success").asBoolean(false)) {
                throw new IllegalStateException(textOrEmpty(node, "message"));
            }
            String country = textOrEmpty(node, "country");
            String region = textOrEmpty(node, "region");
            String city = textOrEmpty(node, "city");
            String isp = textOrEmpty(node, "isp");
            String location = joinParts(city, region, country);
            data.put("success", true);
            data.put("location", location.isBlank() ? "Unknown" : location);
            data.put("country", country);
            data.put("region", region);
            data.put("city", city);
            data.put("isp", isp);
        } catch (Exception e) {
            data.put("success", false);
            data.put("message", e.getMessage() != null ? e.getMessage() : "国外查询失败");
        }
        return data;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("").trim();
    }

    private static String joinParts(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private static String extractIspFromAddr(String addr) {
        if (addr == null || addr.isBlank()) {
            return "";
        }
        int space = addr.lastIndexOf(' ');
        if (space >= 0 && space < addr.length() - 1) {
            return addr.substring(space + 1).trim();
        }
        return "";
    }

    private static boolean isValidIpv4(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                int n = Integer.parseInt(part);
                if (n < 0 || n > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private RestTemplate utf8RestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        RestTemplate rest = new RestTemplate(factory);
        rest.getMessageConverters().removeIf(c -> c instanceof StringHttpMessageConverter);
        rest.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return rest;
    }

    private RestTemplate gbkRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        RestTemplate rest = new RestTemplate(factory);
        rest.getMessageConverters().removeIf(c -> c instanceof StringHttpMessageConverter);
        rest.getMessageConverters().add(0, new StringHttpMessageConverter(Charset.forName("GBK")));
        return rest;
    }
}
