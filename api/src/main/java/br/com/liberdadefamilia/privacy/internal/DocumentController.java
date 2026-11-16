package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The terms of use and the privacy policy: which versions are in force and whether the person accepted them. */
@RestController
class DocumentController {

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/v1/me/documents")
    List<DocumentStatus> listMyDocuments(CurrentUser user) {
        return service.list(user.id());
    }

    @PostMapping("/v1/me/documents/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void acceptDocuments(@Valid @RequestBody AcceptDocumentsRequest request, CurrentUser user) {
        service.accept(user.id(), request);
    }
}
