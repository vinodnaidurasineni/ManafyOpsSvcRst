package com.manafy.ops.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;

/**
 * Converts a validated Amazon Cognito JWT into a Spring authentication token.
 *
 * Cognito only AUTHENTICATES. It does NOT decide what the user can do — we derive
 * NO Spring authorities from Cognito groups/claims. Manafy's own roles/permissions
 * (resolved from PostgreSQL) are the single source of authorization truth. The
 * token establishes identity only (the {@code sub}).
 */
public class CognitoJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Collections.emptyList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
