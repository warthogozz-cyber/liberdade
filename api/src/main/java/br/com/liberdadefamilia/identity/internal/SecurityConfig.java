package br.com.liberdadefamilia.identity.internal;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import br.com.liberdadefamilia.shared.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * One filter chain for the whole API: Google sign-in with PKCE, passkeys, a limit on sign-in attempts, a server-side
 * session that keeps only the user id, behind a cookie that lasts 30 days once signed in, CSRF tokens sent in a
 * header, CORS for the web app only, and problem responses instead of login redirects.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfig {

    static final String CSRF_HEADER = "X-XSRF-TOKEN";

    /** {@code __Host-}: the browser only keeps it when Secure, on path / and without a Domain. */
    static final String SESSION_COOKIE = "__Host-sid";

    /**
     * How long a signed-in session lasts without use. Every session starts at {@code spring.session.timeout} (30
     * minutes), so the ones opened before sign-in, by {@code GET /v1/csrf} or the trip to Google, stay short. Both
     * sign-ins raise it through the one {@link #signedInIdleTimeout() session strategy}.
     */
    static final Duration SIGNED_IN_IDLE_TIMEOUT = Duration.ofDays(30);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            IdentityProperties properties,
            ClientRegistrationRepository clients,
            LiberdadeOidcUserService oidcUserService,
            NoOpAuthorizedClientRepository authorizedClients,
            SecurityProblemHandlers problems,
            UserAccounts accounts,
            @Qualifier("loginRateLimiter") RateLimiter limiter,
            MinimalSecurityContextRepository contexts,
            PersistentSessionCookieSerializer cookies,
            Clock clock,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        String origin = properties.web().origin();
        RedirectStrategy redirects = new DefaultRedirectStrategy();
        // Shared rather than a bean: a UserDetailsService bean would also turn on username and password sign-in.
        http.setSharedObject(UserDetailsService.class, new PasskeyUserDetailsService(accounts));

        // The contract route exists only in dev and test (D32); elsewhere it answers 404.
        http.authorizeHttpRequests(requests -> requests.requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v1/csrf",
                                // Whoever holds the link may look at it and take it, signed in or not.
                                "/v1/invitations/preview",
                                "/v1/invitations/accept",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/error")
                        .permitAll()
                        // Asaas calls this with a token of its own, never with a session (D15).
                        .requestMatchers(HttpMethod.POST, "/v1/billing/webhooks/asaas")
                        .permitAll()
                        // The public terms and policy pages ask which version to show, before any sign-in.
                        .requestMatchers(HttpMethod.GET, "/v1/legal/current")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(login -> login.loginPage(origin + "/entrar")
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                                new ReauthAuthorizationRequestResolver(pkce(clients))))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .authorizedClientRepository(authorizedClients)
                        .successHandler((request, response, authentication) ->
                                redirects.sendRedirect(request, response, origin + "/"))
                        // A plain redirect: unlike the default handler, it keeps the exception out of the session.
                        .failureHandler((request, response, exception) ->
                                redirects.sendRedirect(request, response, origin + "/entrar?erro=login")))
                // Answers {"redirectUrl":"/","authenticated":true}, or a 401 problem when the passkey fails.
                .webAuthn(webAuthn -> webAuthn.rpName(properties.webauthn().rpName())
                        .rpId(properties.webauthn().rpId())
                        .allowedOrigins(origin)
                        .disableDefaultRegistrationPage(true)
                        .withObjectPostProcessor(passkeySignIn(http, contexts, problems, resolver)))
                // Google's sign-in picks it up from here; the passkey's gets it in passkeySignIn.
                .securityContext(context -> context.securityContextRepository(contexts))
                // Runs on every sign-in, before the session gets a new id (fixation protection).
                .sessionManagement(session -> session.addSessionAuthenticationStrategy(signedInIdleTimeout()))
                .addFilterAfter(
                        new PasskeyRegistrationNeedsSignInFilter(
                                problems, SecurityContextHolder.getContextHolderStrategy()),
                        ExceptionTranslationFilter.class)
                // Added before the problem filter below, so that one still runs right before the registration filter.
                .addFilterAfter(new SessionCookieRefreshFilter(clock, cookies), AuthorizationFilter.class)
                // Runs before the authorization check (ExceptionTranslationFilter sits ahead of AuthorizationFilter),
                // once the session's sign-in is known. That is also ahead of Spring's passkey registration filters,
                // which sit after the authorization check, so registration is shut for a pending account as well.
                .addFilterBefore(new PendingDeletionFilter(accounts, resolver), ExceptionTranslationFilter.class)
                // Spring adds its registration filter at this same position later, while the chain is built; the
                // sort is stable, so this one runs right before it.
                .addFilterAfter(new PasskeyRegistrationProblemFilter(resolver), AuthorizationFilter.class)
                .addFilterBefore(new LoginRateLimitFilter(limiter, resolver, properties), CsrfFilter.class)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens())
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(pathPattern(HttpMethod.POST, "/v1/billing/webhooks/asaas")))
                .cors(cors -> cors.configurationSource(cors(origin)))
                .headers(headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(
                                permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")))
                .logout(logout -> logout.logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    /** The session cookie; Boot backs off its own serializer when it finds this bean. */
    @Bean
    PersistentSessionCookieSerializer cookieSerializer() {
        return new PersistentSessionCookieSerializer();
    }

    /**
     * Saves new session attributes with {@code ON CONFLICT DO UPDATE}. Two requests of one session can add the same
     * attribute at once (the cookie refresh mark on the first requests after sign-in, a double tap); the plain insert
     * would fail the second with a duplicate key.
     */
    @Bean
    PostgreSqlJdbcIndexedSessionRepositoryCustomizer sessionAttributeUpsert() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }

    /** Shared by both sign-ins, so neither stores more than the user id in the session. */
    @Bean
    MinimalSecurityContextRepository securityContextRepository(Clock clock, ApplicationEventPublisher events) {
        return new MinimalSecurityContextRepository(new HttpSessionSecurityContextRepository(), clock, events);
    }

    @Bean
    LiberdadeOidcUserService oidcUserService(UserAccounts accounts) {
        return new LiberdadeOidcUserService(new OidcUserService(), accounts);
    }

    /** At most 20 sign-in attempts a minute per address (D10). */
    @Bean
    RateLimiter loginRateLimiter(Clock clock) {
        return new RateLimiter(clock, 20, Duration.ofMinutes(1));
    }

    /**
     * Raises the session to {@link #SIGNED_IN_IDLE_TIMEOUT} on every sign-in, with Google or with a passkey, and marks
     * the request so the cookie written with the new session id lasts as long.
     */
    static SessionAuthenticationStrategy signedInIdleTimeout() {
        return (authentication, request, response) -> {
            request.getSession().setMaxInactiveInterval(Math.toIntExact(SIGNED_IN_IDLE_TIMEOUT.toSeconds()));
            request.setAttribute(PersistentSessionCookieSerializer.PERSISTENT, Boolean.TRUE);
        };
    }

    /**
     * The passkey sign-in runs the same session strategy as Google's (by default it runs none, so the session would
     * keep its id and its 30 minutes) and stores the same minimal context (its filter builds its own repository and
     * ignores the shared one), and a failed passkey gets the same 401 problem as any other request, while a server
     * fault behind it gets the generic 500.
     */
    private static ObjectPostProcessor<WebAuthnAuthenticationFilter> passkeySignIn(
            HttpSecurity http,
            MinimalSecurityContextRepository contexts,
            SecurityProblemHandlers problems,
            HandlerExceptionResolver resolver) {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                // Read while the chain is built: session management shares its strategy only from then on.
                filter.setSessionAuthenticationStrategy(http.getSharedObject(SessionAuthenticationStrategy.class));
                filter.setSecurityContextRepository(contexts);
                filter.setAuthenticationFailureHandler(new PasskeySignInFailureHandler(problems, resolver));
                return filter;
            }
        };
    }

    private static DefaultOAuth2AuthorizationRequestResolver pkce(ClientRegistrationRepository clients) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clients, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    private static HttpSessionCsrfTokenRepository csrfTokens() {
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName(CSRF_HEADER);
        return repository;
    }

    private static CorsConfigurationSource cors(String origin) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", CSRF_HEADER, "Idempotency-Key"));
        config.setExposedHeaders(List.of("Retry-After", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
