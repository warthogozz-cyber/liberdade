package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.shared.TextPatterns;
import br.com.liberdadefamilia.workspace.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creates the family on first sign-in. Responds 201 with no {@code Location}: there is no route to read it (D22). */
@RestController
class WorkspaceController {

    static final int MAX_NAME_LENGTH = 60;

    /** The spec's default, for browsers that send no time zone or one Java does not know (D28). */
    static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("America/Fortaleza");

    private final WorkspaceService service;

    WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @PostMapping("/v1/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceView createWorkspace(@Valid @RequestBody CreateWorkspaceRequest request, CurrentUser user) {
        UUID id = service.create(user, request.name(), request.zoneOrDefault());
        return new WorkspaceView(id, request.name(), Role.OWNER);
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
