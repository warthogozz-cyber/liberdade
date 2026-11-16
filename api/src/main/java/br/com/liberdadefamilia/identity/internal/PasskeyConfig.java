package br.com.liberdadefamilia.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;

/** Where passkeys are kept: the tables of {@code V2__passkeys.sql}, through Spring Security's JDBC repositories. */
@Configuration(proxyBeanMethods = false)
class PasskeyConfig {

    @Bean
    JdbcUserCredentialRepository userCredentials(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    FriendlyUserEntityRepository userEntities(JdbcOperations jdbc, UserAccounts accounts) {
        return new FriendlyUserEntityRepository(new JdbcPublicKeyCredentialUserEntityRepository(jdbc), accounts);
    }
}
