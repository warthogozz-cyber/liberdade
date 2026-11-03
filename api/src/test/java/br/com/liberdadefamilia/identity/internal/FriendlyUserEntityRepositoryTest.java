package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.liberdadefamilia.identity.internal.UserAccounts.UserRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

class FriendlyUserEntityRepositoryTest {

    private final UUID userId = UUID.randomUUID();
    private final UserAccounts accounts = mock(UserAccounts.class);
    private final InMemoryUserEntities stored = new InMemoryUserEntities();
    private final FriendlyUserEntityRepository repository = new FriendlyUserEntityRepository(stored, accounts);

    @Test
    void createsEntityNamedByUserId() {
        activeUser("ana@example.com");

        PublicKeyCredentialUserEntity found = repository.findByUsername(userId.toString());

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("ana@example.com");
        assertThat(found.getDisplayName()).isEqualTo("Ana Teste");
        PublicKeyCredentialUserEntity saved = stored.findById(found.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getName()).isEqualTo(userId.toString());
        assertThat(saved.getDisplayName()).isEqualTo("Ana Teste");
    }

    @Test
    void fallsBackToDisplayNameWithoutEmail() {
        activeUser(null);

        PublicKeyCredentialUserEntity found = repository.findByUsername(userId.toString());

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Ana Teste");
    }

    @Test
    void reusesExistingEntity() {
        activeUser("ana@example.com");

        PublicKeyCredentialUserEntity first = repository.findByUsername(userId.toString());
        PublicKeyCredentialUserEntity second = repository.findByUsername(userId.toString());

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(stored.saves).isOne();
    }

    @Test
    void findByIdReturnsStoredEntity() {
        activeUser("ana@example.com");
        PublicKeyCredentialUserEntity found = repository.findByUsername(userId.toString());
        assertThat(found).isNotNull();

        PublicKeyCredentialUserEntity byId = repository.findById(found.getId());

        assertThat(byId).isNotNull();
        assertThat(byId.getName()).isEqualTo(userId.toString());
    }

    @Test
    void saveKeepsStoredName() {
        activeUser("ana@example.com");
        PublicKeyCredentialUserEntity friendly = repository.findByUsername(userId.toString());
        assertThat(friendly).isNotNull();

        repository.save(friendly);

        PublicKeyCredentialUserEntity saved = stored.findById(friendly.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getName()).isEqualTo(userId.toString());
    }

    @Test
    void inactiveUserIsNotCreated() {
        when(accounts.findActive(userId)).thenReturn(Optional.empty());

        assertThat(repository.findByUsername(userId.toString())).isNull();
        assertThat(repository.findByUsername("not-a-user-id")).isNull();
        assertThat(stored.saves).isZero();
    }

    /** Two first requests at once (a double tap): the one that loses the insert reads the winner's entity. */
    @Test
    void concurrentCreationReusesTheStoredEntity() {
        activeUser("ana@example.com");
        Bytes winner = Bytes.random();
        stored.racedBy(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(winner)
                .name(userId.toString())
                .displayName("Ana Teste")
                .build());

        PublicKeyCredentialUserEntity found = repository.findByUsername(userId.toString());

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(winner);
        assertThat(found.getName()).isEqualTo("ana@example.com");
    }

    @Test
    void deletePassesThrough() {
        Bytes id = Bytes.random();
        stored.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(id)
                .name(userId.toString())
                .displayName("Ana Teste")
                .build());

        repository.delete(id);

        assertThat(stored.findById(id)).isNull();
    }

    private void activeUser(@Nullable String email) {
        when(accounts.findActive(userId)).thenReturn(Optional.of(new UserRecord(userId, "Ana Teste", email)));
    }

    /** Behaves like the JDBC repository: one entity per id and per name, updated in place by id. */
    private static final class InMemoryUserEntities implements PublicKeyCredentialUserEntityRepository {

        private final Map<Bytes, PublicKeyCredentialUserEntity> byId = new LinkedHashMap<>();
        private @Nullable PublicKeyCredentialUserEntity racer;
        int saves;

        /** On the next insert, another request has just stored this entity under the same name. */
        void racedBy(PublicKeyCredentialUserEntity entity) {
            racer = entity;
        }

        @Override
        public @Nullable PublicKeyCredentialUserEntity findById(Bytes id) {
            return byId.get(id);
        }

        @Override
        public @Nullable PublicKeyCredentialUserEntity findByUsername(String username) {
            return byId.values().stream()
                    .filter(entity -> entity.getName().equals(username))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void save(PublicKeyCredentialUserEntity entity) {
            if (racer != null) {
                byId.put(racer.getId(), racer);
                racer = null;
            }
            boolean nameTaken = byId.values().stream()
                    .anyMatch(other -> other.getName().equals(entity.getName())
                            && !other.getId().equals(entity.getId()));
            if (nameTaken) {
                throw new DuplicateKeyException("user_entities_name_key");
            }
            saves++;
            byId.put(entity.getId(), entity);
        }

        @Override
        public void delete(Bytes id) {
            byId.remove(id);
        }
    }
}
