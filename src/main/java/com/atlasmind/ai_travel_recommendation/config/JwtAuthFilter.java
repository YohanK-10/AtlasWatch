package com.atlasmind.ai_travel_recommendation.config;

import com.atlasmind.ai_travel_recommendation.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component // The class itself becomes a bean
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization"); // This contains the JWT Token
        final String jwtToken;
        final String theUserName;
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        jwtToken = authHeader.substring(7); //Everything from the 8th character.
        try {
            theUserName = jwtService.extractUsername(jwtToken);
            if (theUserName != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userInfo = this.userDetailsService.loadUserByUsername(theUserName);
                if (jwtService.isTokenValid(jwtToken, userInfo)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userInfo, null, userInfo.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // This is important!!
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            filterChain.doFilter(request, response);
        } catch (NoSuchAlgorithmException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid encryption Algorithm");
        } catch (InvalidKeySpecException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid key specification");
        }
    }
}
