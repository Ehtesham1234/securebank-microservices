package com.ehtesham.securebank.security.filter;

import com.ehtesham.securebank.security.service.CustomUserDetailsService;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import com.ehtesham.securebank.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_DETAILS_ATTRIBUTE =
            "AUTHENTICATED_USER_DETAILS";

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService customUserDetailsService) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // ── PATH 1: Request from API Gateway ──────────────────────
        // Gateway has already validated the JWT and extracted the
        // claims. We trust these headers because they come from
        // the internal Docker network — external clients cannot
        // set them directly through the gateway.
        String userIdFromGateway = request.getHeader("X-User-Id");

        if (userIdFromGateway != null
                && !userIdFromGateway.isBlank()) {

            String role =
                    request.getHeader("X-User-Role");
            String email =
                    request.getHeader("X-User-Email");
            String userStatus =
                    request.getHeader("X-User-Status");

            try {
                Long userId = Long.parseLong(userIdFromGateway);

                List<SimpleGrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority(
                                role != null ? role : ""));

                // Build a minimal CustomUserPrincipal from
                // headers so UserStatusFilter still works
                // without hitting the DB
                CustomUserPrincipal principal =
                        customUserDetailsService
                                .buildPrincipalFromHeaders(
                                        userId, email, role,
                                        userStatus);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                principal, null,
                                authorities);
                auth.setDetails(userId);

                SecurityContextHolder.getContext()
                        .setAuthentication(auth);

                // Store so UserStatusFilter can read it
                // without a DB query — same pattern as PATH 2
                request.setAttribute(
                        USER_DETAILS_ATTRIBUTE, principal);

            } catch (NumberFormatException e) {
                // Invalid header — skip, let Security block it
            }

            filterChain.doFilter(request, response);
            return;
        }

        // ── PATH 2: Direct request (local dev, no gateway) ────────
        // Original logic — keep exactly as it was
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null
                || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {
            String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null
                    && SecurityContextHolder.getContext()
                    .getAuthentication() == null) {

                if (jwtService.isTokenValid(jwt)) {

                    UserDetails userDetails =
                            customUserDetailsService
                                    .loadUserByUsername(userEmail);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null,
                                    userDetails.getAuthorities());

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);

                    request.setAttribute(
                            USER_DETAILS_ATTRIBUTE, userDetails);
                }
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {
        return request.getRequestURI()
                .startsWith("/api/v1/auth/");
    }
}