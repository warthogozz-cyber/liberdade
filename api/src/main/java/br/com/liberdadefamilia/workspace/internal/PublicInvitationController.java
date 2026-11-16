package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.SessionSignIn;
import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two routes whoever holds a link may call, signed in or not. They share one budget per address, spent before
 * anything is read, so guessing tokens is slow. The token travels in the body, never in a URL.
 */
@RestController
class PublicInvitationController {

    private final InvitationService service;
    private final RateLimiter limiter;
    private final SessionSignIn sessionSignIn;

    PublicInvitationController(
            InvitationService service,
            @Qualifier("invitationPublicRateLimiter") RateLimiter limiter,
            SessionSignIn sessionSignIn) {
        this.service = service;
        this.limiter = limiter;
        this.sessionSignIn = sessionSignIn;
    }

    @PostMapping("/v1/invitations/preview")
    InvitationPreview previewInvitation(
            @Valid @RequestBody PreviewInvitationRequest request, HttpServletRequest httpRequest) {
        spendBudget(httpRequest);
        return service.preview(request.token());
    }

    @PostMapping("/v1/invitations/accept")
    AcceptedInvitation acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request,
            @Parameter(hidden = true) Optional<CurrentUser> user,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        spendBudget(httpRequest);
        InvitationService.AcceptResult result = service.accept(request, user);
        // The service has committed by now: a session never names a person whose creation was rolled back.
        if (result.signInUserId() != null) {
            sessionSignIn.signIn(httpRequest, httpResponse, result.signInUserId(), result.signInMethod());
        }
        return result.accepted();
    }

    private void spendBudget(HttpServletRequest request) {
        String key = request.getRemoteAddr();
        if (!limiter.tryAcquire(key)) {
            throw new RateLimitedException(limiter.retryAfterSeconds(key));
        }
    }
}
