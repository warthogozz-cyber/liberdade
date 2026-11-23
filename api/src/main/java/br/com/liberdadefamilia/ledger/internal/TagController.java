package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The family's tags: list, rename and archive. Tags are made on a transaction, so there is no create route, and no way
 * to bring an archived one back.
 */
@RestController
class TagController {

    private final TagService service;

    TagController(TagService service) {
        this.service = service;
    }

    /** Every role lists; the youth gets the tags they made and the ones on transactions they see. */
    @GetMapping("/v1/workspaces/{ws}/tags")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    List<TagView> listTags(WorkspaceMember member) {
        return service.list(member);
    }

    @PatchMapping("/v1/workspaces/{ws}/tags/{id}")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    TagView renameTag(@PathVariable UUID id, @Valid @RequestBody RenameTagRequest request, WorkspaceMember member) {
        return service.rename(member, id, request);
    }

    @PostMapping("/v1/workspaces/{ws}/tags/{id}/archive")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @ApiResponse(responseCode = "204", description = "Archived, or already archived")
    ResponseEntity<Void> archiveTag(@PathVariable UUID id, WorkspaceMember member) {
        service.archive(member, id);
        return ResponseEntity.noContent().build();
    }
}
