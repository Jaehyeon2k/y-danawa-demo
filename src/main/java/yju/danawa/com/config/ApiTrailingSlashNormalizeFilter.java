package yju.danawa.com.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class ApiTrailingSlashNormalizeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || uri.length() <= 1 || !uri.startsWith("/api/") || !uri.endsWith("/")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String normalizedUri = uri.replaceAll("/+$", "");
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return normalizedUri;
            }

            @Override
            public String getServletPath() {
                return normalizedUri;
            }
        };
        filterChain.doFilter(wrapped, response);
    }
}

