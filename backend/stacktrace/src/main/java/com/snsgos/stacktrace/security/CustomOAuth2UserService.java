package com.snsgos.stacktrace.security;

import com.snsgos.stacktrace.entity.User;
import com.snsgos.stacktrace.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            User user = userService.processOAuthPostLogin(oAuth2User.getAttributes());
            return UserPrincipal.create(user, oAuth2User.getAttributes());
        } catch (Exception ex) {
            log.error("Error processing OAuth2 user post-login", ex);
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }
}
