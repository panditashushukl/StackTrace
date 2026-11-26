package com.snsgos.stacktrace.security;

import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Handles Google OpenID Connect (OIDC) logins when the "openid" scope is requested.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService extends OidcUserService {

    private final UserService userService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        try {
            User user = userService.processOAuthPostLogin(oidcUser.getClaims());
            return UserPrincipal.create(
                    user,
                    oidcUser.getClaims(),
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo()
            );
        } catch (Exception ex) {
            log.error("Error processing OIDC user post-login", ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("oidc_login_error", ex.getMessage(), null),
                    ex
            );
        }
    }
}
