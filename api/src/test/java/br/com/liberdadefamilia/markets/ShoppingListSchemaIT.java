package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The V42 schema: a product once per list, ordered dismissals, the cascades and the PRODUCT audit target. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShoppingListSchemaIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID ws;

    @BeforeEach
    void family() {
        ws = workspaces.createFor(users.create("Ana Teste", "ana@example.com"));
    }

    @Test
    void productOncePerList() {
        UUID milk = product(ws, "Leite Exemplo 1 L");
        item(ws, milk, "1", 0);

        assertThatThrownBy(() -> item(ws, milk, "2", 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void quantityMustBePositive() {
        UUID milk = product(ws, "Leite Exemplo 1 L");

        assertThatThrownBy(() -> item(ws, milk, "0", 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> item(ws, milk, "0.5", 0)).doesNotThrowAnyException();
    }

    @Test
    void itemCannotPointToAnotherFamilysProduct() {
        UUID other = workspaces.createFor(users.create("Bia Teste", "bia@example.com"));
        UUID foreign = product(other, "Leite Alheio");

        assertThatThrownBy(() -> item(ws, foreign, "1", 0)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dismissalOrdered() {
        UUID first = product(ws, "Arroz Exemplo");
        UUID second = product(ws, "Feijao Exemplo");
        UUID low = first.compareTo(second) < 0 ? first : second;
        UUID high = low.equals(first) ? second : first;

        assertThatThrownBy(() -> dismissal(ws, high, low)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> dismissal(ws, low, low)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> dismissal(ws, low, high)).doesNotThrowAnyException();
    }

    @Test
    void productCascadeRemovesItemsAndDismissals() {
        UUID first = product(ws, "Arroz Exemplo");
        UUID second = product(ws, "Feijao Exemplo");
        UUID low = first.compareTo(second) < 0 ? first : second;
        UUID high = low.equals(first) ? second : first;
        item(ws, first, "1", 0);
        dismissal(ws, low, high);

        jdbc.sql("delete from products where id = :id").param("id", first).update();

        assertThat(count("shopping_list_items")).isZero();
        assertThat(count("product_join_dismissals")).isZero();
    }

    @Test
    void auditAcceptsProduct() {
        assertThatCode(() -> jdbc.sql("insert into audit_events (action, target_type) values ('TEST', 'PRODUCT')")
                        .update())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.sql("insert into audit_events (action, target_type) values ('TEST', 'NOPE')")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table + " where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private UUID product(UUID workspace, String name) {
        return jdbc.sql("insert into products (workspace_id, name) values (:ws, :name) returning id")
                .param("ws", workspace)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private void item(UUID workspace, UUID product, String quantity, int position) {
        jdbc.sql("""
                        insert into shopping_list_items (workspace_id, product_id, quantity, position)
                        values (:ws, :p, cast(:q as numeric), :pos)""")
                .param("ws", workspace)
                .param("p", product)
                .param("q", quantity)
                .param("pos", position)
                .update();
    }

    private void dismissal(UUID workspace, UUID a, UUID b) {
        jdbc.sql("insert into product_join_dismissals (workspace_id, product_a, product_b) values (:ws, :a, :b)")
                .param("ws", workspace)
                .param("a", a)
                .param("b", b)
                .update();
    }
}
