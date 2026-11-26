package br.com.liberdadefamilia.workspace.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The family's optional modules: everyone sees which are on; only an owner or an adult switches one. */
@RestController
class ModuleController {

    private final ModuleRepository repository;

    ModuleController(ModuleRepository repository) {
        this.repository = repository;
    }

    /** Always every module, in the order of {@link ModuleKey}. */
    @GetMapping("/v1/workspaces/{ws}/modules")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    List<ModuleView> listModules(WorkspaceMember member) {
        Set<ModuleKey> enabled = repository.enabledKeys(member.workspaceId());
        return Arrays.stream(ModuleKey.values())
                .map(key -> new ModuleView(key, enabled.contains(key)))
                .toList();
    }

    /**
     * The key is read as text, so a name that is not a module answers 404 {@code module.not-found} instead of a
     * conversion error. Setting the value a module already has answers 200 and changes nothing.
     */
    @PutMapping("/v1/workspaces/{ws}/modules/{key}")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    ModuleView setModule(
            @Parameter(
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "planning",
                                                "indicators",
                                                "gamification",
                                                "ventures",
                                                "markets"
                                            }))
                    @PathVariable
                    String key,
            @Valid @RequestBody UpdateModuleRequest request,
            WorkspaceMember member) {
        ModuleKey module = ModuleKey.fromWire(key).orElseThrow(ModuleNotFoundException::new);
        boolean enabled = request.enabled();
        repository.set(member.workspaceId(), module, enabled, member.userId());
        return new ModuleView(module, enabled);
    }
}
