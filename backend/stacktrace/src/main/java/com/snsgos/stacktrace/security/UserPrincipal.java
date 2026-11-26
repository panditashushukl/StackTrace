package com.snsgos.stacktrace.security;

import com.snsgos.stacktrace.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.*;

@Getter
public class UserPrincipal implements OidcUser, UserDetails {

    private final UUID id;
    private final String email;
    private final String name;
    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public UserPrincipal(UUID id, String email, String name, String username,
                         Collection<? extends GrantedAuthority> authorities,
                         Map<String, Object> attributes) {
        this(id, email, name, username, authorities, attributes, null, null);
    }

    public UserPrincipal(UUID id, String email, String name, String username,
                         Collection<? extends GrantedAuthority> authorities,
                         Map<String, Object> attributes,
                         OidcIdToken idToken,
                         OidcUserInfo userInfo) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.username = username;
        this.authorities = authorities;
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        return create(user, attributes, null, null);
    }

    public static UserPrincipal create(User user, Map<String, Object> attributes,
                                       OidcIdToken idToken, OidcUserInfo userInfo) {
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().name())
        );

        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getUsername(),
                authorities,
                attributes,
                idToken,
                userInfo
        );
    }

    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return name;
    }
}
