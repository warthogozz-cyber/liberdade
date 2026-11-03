package br.com.liberdadefamilia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.AbstractJsonContentAssert;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The parts of the contract the web relies on: money as text, months as {@code yyyy-MM}, nulls marked (D32). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

    private static final String SCHEMAS = "$.components.schemas";
    private static final String TRANSACTIONS = "$.paths['/v1/workspaces/{ws}/transactions']";

    @Autowired
    MockMvcTester mvc;

    @Test
    void moneyIsAStringPair() {
        var contract = contract();

        contract.extractingPath(SCHEMAS + ".Money.required").asArray().containsExactlyInAnyOrder("amount", "currency");
        contract.extractingPath(SCHEMAS + ".Money.properties.amount.type").isEqualTo("string");
        contract.extractingPath(SCHEMAS + ".Money.properties.amount.pattern").isEqualTo("^-?\\d+(\\.\\d{1,4})?$");
        contract.extractingPath(SCHEMAS + ".Money.properties.currency.type").isEqualTo("string");
        contract.extractingPath(SCHEMAS + ".Money.properties.currency.pattern").isEqualTo("^[A-Z]{3}$");
    }

    @Test
    void moneyFieldsReferenceTheComponent() {
        contract()
                .extractingPath(SCHEMAS + ".TransactionView.properties.amount['$ref']")
                .isEqualTo("#/components/schemas/Money");
    }

    @Test
    void monthIsYearMonthPattern() {
        contract()
                .extractingPath(TRANSACTIONS + ".get.parameters[?(@.name == 'month')].schema.pattern")
                .asArray()
                .containsExactly("^\\d{4}-(0[1-9]|1[0-2])$");
    }

    @Test
    void resolvedParametersAreHidden() {
        String accounts = "$.paths['/v1/workspaces/{ws}/accounts'].get.parameters";
        var contract = contract();

        contract.hasPath("$.paths['/v1/me'].get");
        contract.doesNotHavePath("$.paths['/v1/me'].get.parameters");
        contract.hasPath("$.paths['/v1/csrf'].get");
        contract.doesNotHavePath("$.paths['/v1/csrf'].get.parameters");
        contract.doesNotHavePath(SCHEMAS + ".CsrfToken");
        contract.extractingPath(accounts + "[*].name").asArray().containsExactly("ws");
        contract.extractingPath(accounts + "[0].in").isEqualTo("path");
        contract.extractingPath(accounts + "[0].required").isEqualTo(true);
        contract.extractingPath(accounts + "[0].schema.type").isEqualTo("string");
        contract.extractingPath(accounts + "[0].schema.format").isEqualTo("uuid");
    }

    @Test
    void familyRoutesTakeWsOnce() {
        var contract = contract();

        contract.extractingPath(TRANSACTIONS + ".get.parameters[*].name")
                .asArray()
                .containsExactly("ws", "month");
        contract.extractingPath(TRANSACTIONS + ".post.parameters[*].name")
                .asArray()
                .containsExactly("ws", "Idempotency-Key");
    }

    @Test
    void recordingAnswersCreatedOrTheStoredTransaction() {
        var contract = contract();

        contract.extractingPath(TRANSACTIONS + ".post.responses").asMap().containsOnlyKeys("200", "201");
        for (String status : List.of("200", "201")) {
            contract.extractingPath(TRANSACTIONS + ".post.responses['" + status
                            + "'].content['application/json'].schema['$ref']")
                    .isEqualTo("#/components/schemas/TransactionView");
        }
    }

    @Test
    void idempotencyKeyIsRequiredUuid() {
        String header = TRANSACTIONS + ".post.parameters[?(@.name == 'Idempotency-Key')]";
        var contract = contract();

        contract.extractingPath(header + ".in").asArray().containsExactly("header");
        contract.extractingPath(header + ".required").asArray().containsExactly(true);
        contract.extractingPath(header + ".schema.type").asArray().containsExactly("string");
        contract.extractingPath(header + ".schema.format").asArray().containsExactly("uuid");
    }

    @Test
    void serverIsRoot() {
        contract().extractingPath("$.servers").isEqualTo(List.of(Map.of("url", "/")));
    }

    @Test
    void zonesAndCurrenciesAreStrings() {
        var contract = contract();

        contract.extractingPath(SCHEMAS + ".MeWorkspace.properties.timezone.type")
                .isEqualTo("string");
        contract.extractingPath(SCHEMAS + ".MeWorkspace.properties.currency.type")
                .isEqualTo("string");
    }

    @Test
    void nullability() {
        var contract = contract();

        contract.extractingPath(SCHEMAS + ".TransactionView.properties.description.nullable")
                .isEqualTo(true);
        contract.extractingPath(SCHEMAS + ".MeUser.properties.passkeyCreatedAt.nullable")
                .isEqualTo(true);
        contract.extractingPath(SCHEMAS + ".CategoryView.properties.icon.nullable")
                .isEqualTo(true);
        for (String field : List.of(
                "TransactionView.properties.categoryId",
                "TransactionView.properties.amount",
                "MeUser.properties.displayName")) {
            contract.hasPath(SCHEMAS + "." + field);
            contract.doesNotHavePath(SCHEMAS + "." + field + ".nullable");
        }
    }

    /** The ids name the generated operations; a {@code _1} suffix would depend on the order handlers are found. */
    @Test
    void operationIdsAreUniqueAndUnsuffixed() {
        contract()
                .extractingPath("$.paths.*.*.operationId")
                .asArray()
                .isNotEmpty()
                .doesNotHaveDuplicates()
                .noneMatch(id -> id.toString().matches(".*_\\d+$"));
    }

    @Test
    void onlyV1Paths() {
        contract()
                .extractingPath("$.paths")
                .asMap()
                .isNotEmpty()
                .allSatisfy((path, operations) -> assertThat(path).startsWith("/v1/"));
    }

    private AbstractJsonContentAssert<?> contract() {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        return assertThat(result).bodyJson();
    }
}
