package com.ehtesham.securebank.security.filter;

import com.ehtesham.securebank.security.service.CustomUserDetailsService;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import com.ehtesham.securebank.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/*
 * Collapsed from a two-path filter (trust gateway-forwarded, HMAC-signed
 * headers OR independently verify a direct JWT) down to one: every
 * caller — through the gateway, or hitting this service directly —
 * presents a JWT, and it's verified the exact same way regardless. There
 * is no longer a distinction between "came via gateway" and "came
 * directly" to make, so there's nothing left to sign/relay/re-verify.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

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

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {
            if (jwtService.isTokenValid(jwt)
                    && SecurityContextHolder.getContext()
                    .getAuthentication() == null) {

                String email = jwtService.extractUsername(jwt);
                String role = jwtService.extractRole(jwt);
                String userIdStr = jwtService.extractClaim(
                        jwt, claims -> claims.get("userId", String.class));
                String userStatus = jwtService.extractClaim(
                        jwt, claims -> claims.get("userStatus", String.class));

                Long userId = Long.parseLong(userIdStr);

                List<SimpleGrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority(
                                role != null ? role : ""));

                // Minimal principal built straight from verified JWT
                // claims — no DB hit needed, same as before.
                CustomUserPrincipal principal =
                        customUserDetailsService.buildPrincipalFromHeaders(
                                userId, email, role, userStatus);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, authorities);
                authToken.setDetails(userId);

                SecurityContextHolder.getContext()
                        .setAuthentication(authToken);

                request.setAttribute(USER_DETAILS_ATTRIBUTE, principal);
            }
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
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
