package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.SignInHints;
import br.com.liberdadefamilia.shared.TextPatterns;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creates the family on first sign-in and renames it. Creating responds 201 with no {@code Location} (D22). */
@RestController
class WorkspaceController {

    static final int MAX_NAME_LENGTH = 60;

    /** The spec's default, for browsers that send no time zone or one Java does not know (D28). */
    static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("America/Fortaleza");

    private final WorkspaceService service;
    private final SignInHints signInHints;

    WorkspaceController(WorkspaceService service, SignInHints signInHints) {
        this.service = service;
        this.signInHints = signInHints;
    }

    @PostMapping("/v1/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceView createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request, CurrentUser user, HttpServletRequest httpRequest) {
        UUID id = service.create(user, request.name(), request.zoneOrDefault());
        // The family name hint only suggested this family's name; the session need not keep it any longer.
        signInHints.forgetFamilyName(httpRequest);
        return new WorkspaceView(id, request.name(), Role.OWNER);
    }

    @PatchMapping("/v1/workspaces/{ws}")
    @WorkspaceScoped(allow = Role.OWNER)
    MeWorkspace renameWorkspace(@Valid @RequestBody RenameWorkspaceRequest request, WorkspaceMember member) {
        return service.rename(member, request.name());
    }

    /** @param timezone the browser's IANA time zone */
    record CreateWorkspaceRequest(
            @NotBlank @Size(max = MAX_NAME_LENGTH) @Pattern(regexp = TextPatterns.WITHOUT_NUL)
            String name,

            @Nullable @Schema(nullable = true) String timezone) {

        CreateWorkspaceRequest {
            if (name != null) {
                name = name.strip();
            }
        }

        ZoneId zoneOrDefault() {
            return timezone != null && ZoneId.getAvailableZoneIds().contains(timezone)
                    ? ZoneId.of(timezone)
                    : DEFAULT_TIMEZONE;
        }
    }

    record WorkspaceView(UUID id, String name, Role role) {}
}
