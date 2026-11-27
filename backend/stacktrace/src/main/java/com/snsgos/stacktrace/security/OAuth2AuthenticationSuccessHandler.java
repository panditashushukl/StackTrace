package com.snsgos.stacktrace.security;

import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.service.RefreshTokenService;
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

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;

    @Value("${app.oauth2.authorized-redirect-uri:http://localhost:4200/auth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        User user = resolveUser(authentication.getPrincipal());

        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()
        );
        String refreshToken = refreshTokenService.createRefreshToken(user);

        authCookieService.addAuthCookies(request, response, accessToken, refreshToken);

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }

    private User resolveUser(Object rawPrincipal) {
        if (rawPrincipal instanceof UserPrincipal userPrincipal) {
            return userService.getUserEntity(userPrincipal.getId());
        }
        if (rawPrincipal instanceof OAuth2User oauth2User) {
            return userService.processOAuthPostLogin(oauth2User.getAttributes());
        }
        throw new IllegalStateException("Unsupported OAuth2 principal type: "
                + (rawPrincipal != null ? rawPrincipal.getClass().getName() : "null"));
    }
}
