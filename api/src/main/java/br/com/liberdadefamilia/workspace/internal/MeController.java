package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.Passkeys;
import br.com.liberdadefamilia.identity.SignInHints;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who is signed in and which families they belong to (D9, D21). It lives here, not in {@code identity}, because only
 * this module knows about families.
 */
@RestController
class MeController {

    private static final String NO_FAMILY_NAME = "Minha família";

    private final Passkeys passkeys;
    private final WorkspaceRepository workspaces;
    private final SignInHints signInHints;

    MeController(Passkeys passkeys, WorkspaceRepository workspaces, SignInHints signInHints) {
        this.passkeys = passkeys;
        this.workspaces = workspaces;
        this.signInHints = signInHints;
    }

    /**
     * A user who already has a family needs no suggestion, so the session drops Google's family name here: a returning
     * user never creates a family, and the session keeps only what the sign-in flow still needs.
     */
    @GetMapping("/v1/me")
    MeView me(CurrentUser user, HttpServletRequest request) {
        List<MeWorkspace> active = workspaces.activeWorkspacesOf(user.id());
        String familyNameHint = user.familyNameHint();
        if (!active.isEmpty()) {
            signInHints.forgetFamilyName(request);
            familyNameHint = null;
        }
        return new MeView(
                new MeUser(
                        user.id(),
                        user.displayName(),
                        passkeys.latestCreatedAt(user.id()).orElse(null)),
                active,
                suggestedName(familyNameHint));
    }

    /**
     * The name the family form starts with: "Família" plus the Google family name, or "Minha família" without one (D7).
     * Cut to the 60-character limit, one short when the cut would split a surrogate pair.
     */
    static String suggestedName(@Nullable String familyNameHint) {
        if (familyNameHint == null || familyNameHint.isBlank()) {
            return NO_FAMILY_NAME;
        }
        String name = "Família " + familyNameHint.strip();
        if (name.length() <= WorkspaceController.MAX_NAME_LENGTH) {
            return name;
        }
        int end = WorkspaceController.MAX_NAME_LENGTH;
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end);
    }

    record MeView(MeUser user, List<MeWorkspace> workspaces, String suggestedWorkspaceName) {}

    record MeUser(
            UUID id,
            String displayName,
            @Nullable @Schema(nullable = true) Instant passkeyCreatedAt) {}
}
