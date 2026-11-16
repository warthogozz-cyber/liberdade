package br.com.liberdadefamilia.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Wraps the default resolver and, when the sign-in starts with {@code ?reauth=true}, asks Google to show the account
 * picker ({@code prompt=select_account}) so the person confirms who they are instead of passing silently.
 */
final class ReauthAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    ReauthAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withPrompt(request, delegate.resolve(request));
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withPrompt(request, delegate.resolve(request, clientRegistrationId));
    }

    private static @Nullable OAuth2AuthorizationRequest withPrompt(
            HttpServletRequest request, @Nullable OAuth2AuthorizationRequest authorization) {
        if (authorization == null || !"true".equals(request.getParameter("reauth"))) {
            return authorization;
        }
        Map<String, Object> parameters = new LinkedHashMap<>(authorization.getAdditionalParameters());
        parameters.put("prompt", "select_account");
        return OAuth2AuthorizationRequest.from(authorization)
                .additionalParameters(parameters)
                .build();
    }
}
