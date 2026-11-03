package br.com.liberdadefamilia.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * One filter chain for the whole API: Google sign-in with PKCE, passkeys, a limit on sign-in attempts, a server-side
 * session, CSRF tokens sent in a header, CORS for the web app only, and problem responses instead of login redirects.
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
            LoginRateLimiter limiter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        String origin = properties.web().origin();
        RedirectStrategy redirects = new DefaultRedirectStrategy();
        // Shared rather than a bean: a UserDetailsService bean would also turn on username and password sign-in.
        http.setSharedObject(UserDetailsService.class, new PasskeyUserDetailsService(accounts));

        http.authorizeHttpRequests(requests -> requests.requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/v1/csrf", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(login -> login.loginPage(origin + "/entrar")
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(pkce(clients)))
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
                        .withObjectPostProcessor(passkeySignIn(http, problems, resolver)))
                // Runs on every sign-in, before the session gets a new id (fixation protection).
                .sessionManagement(session -> session.addSessionAuthenticationStrategy(signedInIdleTimeout()))
                .addFilterAfter(
                        new PasskeyRegistrationNeedsSignInFilter(
                                problems, SecurityContextHolder.getContextHolderStrategy()),
                        ExceptionTranslationFilter.class)
                // Spring adds its registration filter at this same position later, while the chain is built; the
                // sort is stable, so this one runs right before it.
                .addFilterAfter(new PasskeyRegistrationProblemFilter(resolver), AuthorizationFilter.class)
                .addFilterBefore(new LoginRateLimitFilter(limiter, resolver, properties), CsrfFilter.class)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens())
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))
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

    /**
     * The session cookie. Set here rather than with {@code server.servlet.session.cookie.*}, which Boot ignores
     * whenever it sees a servlet context it did not start (a WAR, or MockMvc in tests).
     */
    @Bean
    DefaultCookieSerializerCustomizer sessionCookie() {
        return cookie -> {
            cookie.setCookieName(SESSION_COOKIE);
            cookie.setCookiePath("/");
            cookie.setUseSecureCookie(true);
            cookie.setUseHttpOnlyCookie(true);
            cookie.setSameSite("Lax");
        };
    }

    @Bean
    LiberdadeOidcUserService oidcUserService(UserAccounts accounts) {
        return new LiberdadeOidcUserService(new OidcUserService(), accounts);
    }

    /** At most 20 sign-in attempts a minute per address (D10). */
    @Bean
    LoginRateLimiter loginRateLimiter(Clock clock) {
        return new LoginRateLimiter(clock, 20);
    }

    /** Raises the session to {@link #SIGNED_IN_IDLE_TIMEOUT} on every sign-in, with Google or with a passkey. */
    private static SessionAuthenticationStrategy signedInIdleTimeout() {
        return (authentication, request, response) ->
                request.getSession().setMaxInactiveInterval(Math.toIntExact(SIGNED_IN_IDLE_TIMEOUT.toSeconds()));
    }

    /**
     * The passkey sign-in runs the same session strategy as Google's (by default it runs none, so the session would
     * keep its id and its 30 minutes), and a failed passkey gets the same 401 problem as any other request, while a
     * server fault behind it gets the generic 500.
     */
    private static ObjectPostProcessor<WebAuthnAuthenticationFilter> passkeySignIn(
            HttpSecurity http, SecurityProblemHandlers problems, HandlerExceptionResolver resolver) {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                // Read while the chain is built: session management shares its strategy only from then on.
                filter.setSessionAuthenticationStrategy(http.getSharedObject(SessionAuthenticationStrategy.class));
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
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", CSRF_HEADER, "Idempotency-Key"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
