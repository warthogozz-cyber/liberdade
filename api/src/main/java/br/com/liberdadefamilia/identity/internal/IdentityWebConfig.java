package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets controllers take a {@link br.com.liberdadefamilia.identity.CurrentUser} parameter. */
@Configuration(proxyBeanMethods = false)
class IdentityWebConfig implements WebMvcConfigurer {

    static {
        // Resolved from the session, never sent by the client: they stay out of the OpenAPI contract.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class, CsrfToken.class);
    }

    private final UserAccounts accounts;

    IdentityWebConfig(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(accounts));
    }
}
