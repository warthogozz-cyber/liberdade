package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.internal.UserAccounts.UserRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentUser} parameters from the session (the user id and the sign-in hint) and the database. */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();
    private final UserAccounts accounts;

    CurrentUserArgumentResolver(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public CurrentUser resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !trust.isAuthenticated(authentication)) {
            throw new AuthenticationCredentialsNotFoundException("Not signed in");
        }
        UserRecord user = accounts.findActive(userIdOf(authentication))
                .orElseThrow(() -> new InsufficientAuthenticationException("User is gone or asked for deletion"));
        return new CurrentUser(user.id(), user.displayName(), familyNameHintOf(webRequest));
    }

    private static @Nullable String familyNameHintOf(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = request == null ? null : request.getSession(false);
        return session != null && session.getAttribute(SessionSignInHints.FAMILY_NAME_HINT) instanceof String hint
                ? hint
                : null;
    }

    private static UUID userIdOf(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException notAUserId) {
            throw new InsufficientAuthenticationException("Principal name is not a user id", notAUserId);
        }
    }
}
