package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TagChanged;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The family's tags (D4, D5). A tag is made by typing its name on a transaction; {@link #resolve} finds it by name,
 * letter case aside, or makes it. Error messages and logs never carry a tag name.
 */
@Service
class TagService {

    /** The most active tags a family may have. */
    static final int MAX_ACTIVE = 200;

    private final TagRepository tags;
    private final AccountRepository accounts;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    TagService(TagRepository tags, AccountRepository accounts, Clock clock, ApplicationEventPublisher events) {
        this.tags = tags;
        this.accounts = accounts;
        this.clock = clock;
        this.events = events;
    }

    /** The active tags the member may see. */
    List<TagView> list(WorkspaceMember member) {
        return tags.listActive(member.workspaceId(), member.membershipId(), accounts.scopeFor(member));
    }

    /**
     * The ids of the tags with these names, in the order of the names, each tag once. A name that matches an active tag
     * (even one the youth does not see) uses it; any other makes a tag, spelled as first written. Meant to run inside
     * the transaction that saves the transaction.
     *
     * @throws TagRejectedException when a new tag would be the family's 201st active one
     */
    List<UUID> resolve(WorkspaceMember member, @Nullable List<String> names) {
        if (names == null) {
            return List.of();
        }
        UUID workspaceId = member.workspaceId();
        Map<String, UUID> resolved = new LinkedHashMap<>();
        for (String raw : names) {
            String name = raw.strip();
            if (!resolved.containsKey(TagNamesValidator.key(name))) {
                resolved.put(TagNamesValidator.key(name), resolveOne(workspaceId, member.membershipId(), name));
            }
        }
        return List.copyOf(resolved.values());
    }

    private UUID resolveOne(UUID workspaceId, UUID membershipId, String name) {
        Optional<UUID> existing = tags.findActiveIdByName(workspaceId, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (tags.countActive(workspaceId) >= MAX_ACTIVE) {
            throw new TagRejectedException(
                    "tag.limit", "A família já tem " + MAX_ACTIVE + " tags. Arquive alguma para criar outra.");
        }
        // Another request may have made the name since the lookup: the first spelling stays.
        return tags.insertIfAbsent(workspaceId, name, membershipId)
                .or(() -> tags.findActiveIdByName(workspaceId, name))
                .orElseThrow(() -> new IllegalStateException("tag vanished after insert conflict"));
    }

    /**
     * @throws LedgerNotFoundException when the family has no such active tag
     * @throws LedgerConflictException when another active tag has the name, letter case aside
     */
    @Transactional
    TagView rename(WorkspaceMember member, UUID id, RenameTagRequest request) {
        UUID workspaceId = member.workspaceId();
        try {
            if (!tags.rename(workspaceId, id, request.name())) {
                throw notFound();
            }
        } catch (DuplicateKeyException e) {
            throw new LedgerConflictException("tag.name-taken", "Já existe uma tag com esse nome.");
        }
        events.publishEvent(new TagChanged(workspaceId, id));
        return tags.findActive(workspaceId, id).orElseThrow(TagService::notFound);
    }

    /**
     * Archives the tag; it stays on the transactions that have it. Archiving an archived tag changes nothing.
     *
     * @throws LedgerNotFoundException when the family has no such tag
     */
    @Transactional
    void archive(WorkspaceMember member, UUID id) {
        if (!tags.archive(member.workspaceId(), id, clock.instant())) {
            throw notFound();
        }
    }

    private static LedgerNotFoundException notFound() {
        return new LedgerNotFoundException("tag.not-found", "Tag não encontrada.");
    }
}
