package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticación Stateless que valida el encabezado Authorization Bearer JWT
 * en cada solicitud HTTP hacia los endpoints del Probador Virtual (SR-RF-23).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProblemHandler securityProblemHandler;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            SecurityProblemHandler securityProblemHandler
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityProblemHandler = securityProblemHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer", 0, 6)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (header.length() < 8 || header.charAt(6) != ' ') {
                throw new BadCredentialsException("Encabezado Bearer mal formado");
            }
            String rawToken = header.substring(7).trim();
            AuthenticatedUser authenticatedUser = jwtTokenProvider.validateAndExtractUser(rawToken);

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + authenticatedUser.role());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            authenticatedUser,
                            null,
                            List.of(authority)
                    )
            );
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | BadCredentialsException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Token Bearer JWT rechazado en {}: {}", request.getRequestURI(), ex.getMessage());
            securityProblemHandler.commence(
                    request,
                    response,
                    new BadCredentialsException("Token Bearer JWT inválido o expirado", ex)
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}

