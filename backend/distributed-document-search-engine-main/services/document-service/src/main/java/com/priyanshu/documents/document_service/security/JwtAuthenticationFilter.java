package com.priyanshu.documents.document_service.security;

import java.io.IOException;
// import java.util.Collections;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        // 请求没有 JWT，就继续交给后面的过滤器。
        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 删除开头的 "Bearer "，得到真正的 JWT。
        String token = authorizationHeader.substring(7);

        // 当前请求还没有完成身份认证时，才检查 Token。
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && jwtService.isTokenValid(token)) {

            String userId = jwtService.extractUserId(token);
            String tokenType = jwtService.extractTokenType(token);

            List<SimpleGrantedAuthority> authorities;

            if ("service".equals(tokenType)) {
                authorities = List.of(new SimpleGrantedAuthority("SERVICE"));
            } else if ("user".equals(tokenType)) {
                authorities = List.of(new SimpleGrantedAuthority("USER"));
            } else {
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Unsupported token type");
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    authorities);

            authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request));

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}