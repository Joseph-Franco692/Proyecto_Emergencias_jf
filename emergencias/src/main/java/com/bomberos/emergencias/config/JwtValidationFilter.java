package com.bomberos.emergencias.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtValidationFilter implements Filter {

    // Same secret as Node.js
    private final String SECRET = "clave_secreta_super_segura_para_el_proyecto_distribuida_12345";
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Skip validation for WebSockets and specific public paths if needed
        if (path.startsWith("/ws") || path.startsWith("/api/reportes/ciudadano") || req.getMethod().equals("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        // We require JWT for all other /api paths
        if (path.startsWith("/api/")) {
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.getWriter().write("Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                
                // Add claims to request attributes for controllers to use if needed
                req.setAttribute("userEmail", claims.get("email"));
                
            } catch (Exception e) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.getWriter().write("Invalid or expired JWT Token");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
