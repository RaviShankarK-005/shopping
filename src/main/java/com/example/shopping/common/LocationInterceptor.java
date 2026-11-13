package com.example.shopping.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restricts API requests to configured developer IPs or requests identified as London.
 *
 * <p>Static storefront resources and the H2 console are allowed through. The
 * geographic decision may use a CDN city header or an external IP lookup.</p>
 */
@Component
public class LocationInterceptor implements HandlerInterceptor {

	@Value("${shop.allowed-ips:49.206.129.249}")
    private Set<String> ALLOWED_DEV_IPS;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final Map<String, Boolean> ipCache = new ConcurrentHashMap<>();
    private volatile String cachedPublicIp = null;

    /**
     * Allows exempt resources and permitted locations, otherwise responds with HTTP 403.
     *
     * @param request incoming HTTP request
     * @param response outgoing HTTP response
     * @param handler selected MVC handler
     * @return true to continue processing, false after a forbidden response
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/h2-console") || uri.equals("/") || uri.endsWith(".html") || uri.endsWith(".js") || uri.endsWith(".css")) {
            return true;
        }

        String clientIp = getClientIp(request);

        if (!isAllowedLocation(clientIp, request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Service available exclusively in London, UK.\"}");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String ip = request.getRemoteAddr();

        // Resolve actual public IP when testing on localhost
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            if (cachedPublicIp != null) {
                return cachedPublicIp;
            }
            try {
                HttpRequest publicIpReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.ipify.org"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> res = httpClient.send(publicIpReq, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200 && res.body() != null && !res.body().isBlank()) {
                    cachedPublicIp = res.body().trim();
                    return cachedPublicIp;
                }
            } catch (Exception e) {
                System.out.println("******** public ip lookup failed --- " + e);
            }
        }

        return ip;
    }

    private boolean isAllowedLocation(String ip, HttpServletRequest request) {
        System.out.println("******** isAllowedLocation --- " + ip);

        // 1. Allow your explicit developer IP
        if (ALLOWED_DEV_IPS != null && ALLOWED_DEV_IPS.contains(ip)) {
            return true;
        }

        // 2. Check standard CDN city header if present
        String cfCity = request.getHeader("CF-IPCity");
        if (cfCity != null) {
            return "London".equalsIgnoreCase(cfCity);
        }

        // 3. Check cache
        if (ipCache.containsKey(ip)) {
            return ipCache.get(ip);
        }

        // 4. Geolocation lookup via GeoIP API (ip-api.com) for London
        try {
            HttpRequest apiReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,countryCode,city"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> apiRes = httpClient.send(apiReq, HttpResponse.BodyHandlers.ofString());
            if (apiRes.statusCode() == 200) {
                String body = apiRes.body();
                boolean isLondon = body.contains("\"city\":\"London\"");
                ipCache.put(ip, isLondon);
                return isLondon;
            }
        } catch (Exception e) {
            System.out.println("******** geo ip api - error --- " + e);
        }

        return false;
    }
}