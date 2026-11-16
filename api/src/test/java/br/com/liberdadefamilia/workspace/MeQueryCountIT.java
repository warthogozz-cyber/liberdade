package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** {@code GET /v1/me} costs a fixed number of statements, however many families the person is in. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MeQueryCountIT.Counting.class})
class MeQueryCountIT {

    static final AtomicInteger STATEMENTS = new AtomicInteger();

    /** Set around the call under test, on the request thread only: statements of other threads never count. */
    static final ThreadLocal<Boolean> COUNTING = ThreadLocal.withInitial(() -> false);

    @TestConfiguration(proxyBeanMethods = false)
    static class Counting {

        @Bean
        static BeanPostProcessor countingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof DataSource source) || bean instanceof Proxy) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (p, m, a) -> {
                                Object result = invoke(source, m, a);
                                if (result instanceof Connection connection) {
                                    return wrap(connection);
                                }
                                return result;
                            });
                }
            };
        }

        private static Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> {
                        if (COUNTING.get()
                                && (m.getName().equals("prepareStatement")
                                        || m.getName().equals("createStatement"))) {
                            STATEMENTS.incrementAndGet();
                        }
                        return invoke(connection, m, a);
                    });
        }

        private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void statementsDoNotGrowWithTheFamilies() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        workspaces.createFor(ana);
        int one = statementsForMe(ana);

        for (int i = 0; i < 3; i++) {
            UUID other = users.create("Outra " + i, "outra" + i + "@example.com");
            workspaces.addMember(workspaces.createFor(other), ana, Role.ADULT);
        }
        int four = statementsForMe(ana);

        assertThat(four).isEqualTo(one);
    }

    private int statementsForMe(UUID userId) {
        STATEMENTS.set(0);
        COUNTING.set(true);
        try {
            assertThat(mvc.get()
                            .uri("/v1/me")
                            .with(TestUsers.signedIn(userId, null))
                            .exchange())
                    .hasStatusOk();
        } finally {
            COUNTING.set(false);
        }
        return STATEMENTS.get();
    }
}
