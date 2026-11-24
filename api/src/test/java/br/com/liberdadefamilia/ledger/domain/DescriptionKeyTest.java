package br.com.liberdadefamilia.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DescriptionKeyTest {

    @Test
    void vectors() {
        Map<String, String> vectors = new LinkedHashMap<>();
        vectors.put("PADARIA SÃO JOÃO", "padaria sao joao");
        vectors.put("  Uber   *Trip  ", "uber trip");
        vectors.put("LOJA X PARC 03/10", "loja x");
        vectors.put("Loja X parcela 3 de 10", "loja x");
        vectors.put("LOJA X 03/10", "loja x");
        vectors.put("Pgto. fatura—cartão", "pgto fatura cartao");
        vectors.put("", "");
        vectors.put("Ação 2025", "acao 2025");

        vectors.forEach(
                (input, key) -> assertThat(DescriptionKey.of(input)).as(input).isEqualTo(key));
    }

    @Test
    void nullIsEmpty() {
        assertThat(DescriptionKey.of(null)).isEmpty();
    }
}
