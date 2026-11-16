package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.internal.UserAccounts.UserRecord;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

/**
 * Keeps {@code users.id} as the stored name of a passkey's user entity, which is what Spring signs the passkey in as,
 * while the options sent to the browser carry the email, the name the device's password manager shows (D18).
 */
final class FriendlyUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private final PublicKeyCredentialUserEntityRepository delegate;
    private final UserAccounts accounts;

    FriendlyUserEntityRepository(PublicKeyCredentialUserEntityRepository delegate, UserAccounts accounts) {
        this.delegate = delegate;
        this.accounts = accounts;
    }

    /**
     * The entity of an active user, created on first use, with the email (or the display name) as its name. Anything
     * else is looked up as it is, and never created.
     */
    @Override
    public @Nullable PublicKeyCredentialUserEntity findByUsername(String username) {
        Optional<UserRecord> user = PasskeyUserDetailsService.userIdOf(username).flatMap(accounts::findActive);
        if (user.isEmpty()) {
            return delegate.findByUsername(username);
        }
        PublicKeyCredentialUserEntity stored = delegate.findByUsername(username);
        if (stored == null) {
            stored = create(user.get());
        }
        String email = user.get().email();
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(stored.getId())
                .name(email != null ? email : user.get().displayName())
                .displayName(stored.getDisplayName())
                .build();
    }

    /** As stored: its name is the {@code users.id}. */
    @Override
    public @Nullable PublicKeyCredentialUserEntity findById(Bytes id) {
        return delegate.findById(id);
    }

    /** Saving a friendly copy of an existing entity keeps the stored name. */
    @Override
    public void save(PublicKeyCredentialUserEntity entity) {
        PublicKeyCredentialUserEntity stored = delegate.findById(entity.getId());
        if (stored == null) {
            delegate.save(entity);
            return;
        }
        delegate.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(entity.getId())
                .name(stored.getName())
                .displayName(entity.getDisplayName())
                .build());
    }

    @Override
    public void delete(Bytes id) {
        delegate.delete(id);
    }

    /** When two first requests race (a double tap), the unique name lets one insert win and the other reads it. */
    private PublicKeyCredentialUserEntity create(UserRecord user) {
        String name = user.id().toString();
        PublicKeyCredentialUserEntity entity = ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random())
                .name(name)
                .displayName(user.displayName())
                .build();
        try {
            delegate.save(entity);
            return entity;
        } catch (DuplicateKeyException race) {
            PublicKeyCredentialUserEntity winner = delegate.findByUsername(name);
            if (winner == null) {
                throw race;
            }
            return winner;
        }
    }
}
