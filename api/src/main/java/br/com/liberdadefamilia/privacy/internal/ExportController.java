package br.com.liberdadefamilia.privacy.internal;

import static br.com.liberdadefamilia.workspace.Role.OWNER;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.RequiresRecentSignIn;
import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * The data export: a zip of JSON and spreadsheet-safe CSV. Both routes need a recent sign-in and share a budget of
 * ten an hour per person, counted after the role and sign-in checks so a refused call spends nothing.
 */
@RestController
class ExportController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final ExportService service;
    private final RateLimiter limiter;

    ExportController(ExportService service, @Qualifier("exportRateLimiter") RateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    /** Everything the person has in Liberdade, across all their families. */
    @RequiresRecentSignIn
    @PostMapping("/v1/me/export")
    @ApiResponse(
            responseCode = "200",
            description = "A zip with the person's data.",
            headers =
                    @Header(
                            name = "Content-Disposition",
                            description = "An attachment with the file name of the zip.",
                            schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<StreamingResponseBody> exportMyData(CurrentUser user) {
        spend(user);
        return zip(service.exportMine(user.id()));
    }

    /** Everything the family has, for its owner. */
    @RequiresRecentSignIn
    @PostMapping("/v1/workspaces/{ws}/export")
    @WorkspaceScoped(
            allow = {OWNER},
            includePendingDeletion = true)
    @ApiResponse(
            responseCode = "200",
            description = "A zip with the family's data.",
            headers =
                    @Header(
                            name = "Content-Disposition",
                            description = "An attachment with the file name of the zip.",
                            schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<StreamingResponseBody> exportFamilyData(WorkspaceMember member, CurrentUser user) {
        spend(user);
        return zip(service.exportFamily(member.workspaceId(), user.id()));
    }

    private void spend(CurrentUser user) {
        String key = user.id().toString();
        if (!limiter.tryAcquire(key)) {
            throw new RateLimitedException(limiter.retryAfterSeconds(key));
        }
    }

    private static ResponseEntity<StreamingResponseBody> zip(ExportService.Archive archive) {
        return ResponseEntity.ok()
                .contentType(ZIP)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(archive.filename())
                                .build()
                                .toString())
                .body(archive.body());
    }
}
