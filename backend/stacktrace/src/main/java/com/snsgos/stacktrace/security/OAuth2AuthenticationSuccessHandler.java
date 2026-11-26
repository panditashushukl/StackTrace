package com.snsgos.stacktrace.security;

import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserService userService;

    @Value("${app.oauth2.authorized-redirect-uri:http://localhost:4200/auth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UserPrincipal principal = resolvePrincipal(authentication.getPrincipal());

        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElse("ROLE_USER");

        String token = jwtService.generateToken(
                principal.getId(),
                principal.getEmail(),
                principal.getName(),
                role
        );

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .build().toUriString();

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private UserPrincipal resolvePrincipal(Object rawPrincipal) {
        if (rawPrincipal instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        if (rawPrincipal instanceof OAuth2User oauth2User) {
            User user = userService.processOAuthPostLogin(oauth2User.getAttributes());
            return UserPrincipal.create(user, oauth2User.getAttributes());
        }
        throw new IllegalStateException("Unsupported OAuth2 principal type: "
                + (rawPrincipal != null ? rawPrincipal.getClass().getName() : "null"));
    }
}
