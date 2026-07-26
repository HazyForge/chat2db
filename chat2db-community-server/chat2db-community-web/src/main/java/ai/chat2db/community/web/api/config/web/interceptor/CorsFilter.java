package ai.chat2db.community.web.api.config.web.interceptor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

import ai.chat2db.community.tools.util.ConfigUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;


@Component
public class CorsFilter implements Filter {

    private static final Set<String> DEFAULT_COMMUNITY_ALLOWED_ORIGINS = Set.of(
            "http://127.0.0.1:8888",
            "http://localhost:8888",
            "http://127.0.0.1:10825",
            "http://localhost:10825"
    );

    private final Set<String> communityAllowedOrigins;

    public CorsFilter(@Value("${chat2db.web.cors.allowed-origin:}") String configuredOrigin) {
        this.communityAllowedOrigins = buildAllowedOrigins(configuredOrigin);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse)res;
        HttpServletRequest request = (HttpServletRequest)req;
        String origin = request.getHeader(HttpHeaders.ORIGIN);

        if (ConfigUtils.isCommunity() && !allowCommunityOrigin(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (origin != null) {
            setCorsHeaders(response, origin);
        }
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, DBHUB, uid, Time-Zone");
        chain.doFilter(req, res);
    }

    boolean allowCommunityOrigin(String origin) {
        return origin == null || communityAllowedOrigins.contains(origin);
    }

    private static Set<String> buildAllowedOrigins(String configuredOrigin) {
        Set<String> origins = new HashSet<>(DEFAULT_COMMUNITY_ALLOWED_ORIGINS);
        if (configuredOrigin.isEmpty()) {
            return Set.copyOf(origins);
        }

        validateConfiguredOrigin(configuredOrigin);
        origins.add(configuredOrigin);
        return Set.copyOf(origins);
    }

    private static void validateConfiguredOrigin(String origin) {
        if (!origin.equals(origin.trim()) || origin.chars().anyMatch(Character::isWhitespace) || origin.contains(",")) {
            throw new IllegalArgumentException("chat2db.web.cors.allowed-origin must be one canonical HTTPS origin");
        }

        try {
            URI uri = new URI(origin);
            if (!"https".equals(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getPort() > 65535) {
                throw new IllegalArgumentException(
                        "chat2db.web.cors.allowed-origin must be one canonical HTTPS origin");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "chat2db.web.cors.allowed-origin must be one canonical HTTPS origin", exception);
        }
    }

    private static void setCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }

}
