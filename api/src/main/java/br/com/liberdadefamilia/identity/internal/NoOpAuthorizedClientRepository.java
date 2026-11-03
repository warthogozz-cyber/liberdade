package br.com.liberdadefamilia.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/**
 * Never keeps Google's access or refresh token: Liberdade only needs to know who signed in (D17). Being a bean, it also
 * replaces Boot's in-memory default.
 */
@Component
class NoOpAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
            String clientRegistrationId, @Nullable Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            @Nullable Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Intentionally empty: the token is dropped.
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            @Nullable Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Nothing was saved, so there is nothing to remove.
    }
}
