package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.markets.domain.ProductMatcher.Existing;
import br.com.liberdadefamilia.markets.domain.ProductMatcher.New;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductMatcherTest {

    private static final String GTIN = "4006381333931";
    private static final UUID BY_GTIN = UUID.randomUUID();
    private static final UUID BY_ALIAS = UUID.randomUUID();

    private static ProductLookup lookup(boolean gtin, boolean alias) {
        return new ProductLookup() {
            @Override
            public Optional<UUID> byGtin(String g) {
                return gtin && GTIN.equals(g) ? Optional.of(BY_GTIN) : Optional.empty();
            }

            @Override
            public Optional<UUID> byAlias(String descriptionNorm) {
                return alias && "leite integral".equals(descriptionNorm) ? Optional.of(BY_ALIAS) : Optional.empty();
            }
        };
    }

    @Test
    void gtinFirst() {
        assertThat(ProductMatcher.match(GTIN, "leite integral", lookup(true, true)))
                .isEqualTo(new Existing(BY_GTIN));
    }

    @Test
    void aliasWhenNoGtin() {
        assertThat(ProductMatcher.match(null, "leite integral", lookup(true, true)))
                .isEqualTo(new Existing(BY_ALIAS));
        assertThat(ProductMatcher.match("SEM GTIN", "leite integral", lookup(true, true)))
                .isEqualTo(new Existing(BY_ALIAS));
        assertThat(ProductMatcher.match("4006381333932", "leite integral", lookup(true, true)))
                .isEqualTo(new Existing(BY_ALIAS));
        assertThat(ProductMatcher.match(GTIN, "leite integral", lookup(false, true)))
                .isEqualTo(new Existing(BY_ALIAS));
    }

    @Test
    void newOtherwise() {
        assertThat(ProductMatcher.match(GTIN, "leite integral", lookup(false, false)))
                .isEqualTo(new New());
        assertThat(ProductMatcher.match(null, "outro", lookup(true, true))).isEqualTo(new New());
    }
}
