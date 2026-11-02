package br.com.liberdadefamilia.shared.internal;

import br.com.liberdadefamilia.shared.Money;
import java.util.Currency;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Reads and writes {@link Money} as {@code {"amount":"1234.50","currency":"BRL"}}. The amount is a
 * JSON string holding a plain decimal; numbers and anything ambiguous are rejected. Error messages
 * state the rule only and never repeat the received value.
 */
@JacksonComponent
public class MoneyJacksonComponent {

    public static class Serializer extends ValueSerializer<Money> {

        @Override
        public void serialize(Money value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeStartObject();
            gen.writeStringProperty("amount", value.toPlainString());
            gen.writeStringProperty("currency", value.currency().getCurrencyCode());
            gen.writeEndObject();
        }
    }

    public static class Deserializer extends ValueDeserializer<Money> {

        @Override
        public Money deserialize(JsonParser p, DeserializationContext ctxt) {
            if (p.currentToken() != JsonToken.START_OBJECT) {
                throw mismatch(p, "Money must be a JSON object");
            }
            String amount = null;
            String currencyCode = null;
            while (p.nextToken() == JsonToken.PROPERTY_NAME) {
                String name = p.currentName();
                JsonToken value = p.nextToken();
                if ("amount".equals(name)) {
                    amount = textOrNull(p, value, "Money amount must be a JSON string with a plain decimal");
                } else if ("currency".equals(name)) {
                    currencyCode = textOrNull(p, value, "Money currency must be a JSON string");
                } else {
                    p.skipChildren();
                }
            }
            if (amount == null) {
                throw mismatch(p, "Money amount is required");
            }
            if (currencyCode == null) {
                throw mismatch(p, "Money currency is required");
            }
            return build(p, amount, currencyCode);
        }

        private static String textOrNull(JsonParser p, JsonToken token, String message) {
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token != JsonToken.VALUE_STRING) {
                throw mismatch(p, message);
            }
            return p.getString();
        }

        private static Money build(JsonParser p, String amount, String currencyCode) {
            Currency currency;
            try {
                currency = Currency.getInstance(currencyCode);
            } catch (IllegalArgumentException e) {
                throw mismatch(p, "Money currency must be a known ISO 4217 code");
            }
            int allowed = currency.getDefaultFractionDigits();
            if (allowed < 0) {
                throw mismatch(p, "Money currency must define fraction digits");
            }
            int dot = amount.indexOf('.');
            if (dot >= 0 && amount.length() - dot - 1 > allowed) {
                throw mismatch(p, "Money amount has more decimal places than its currency allows");
            }
            try {
                return Money.parse(amount, currency);
            } catch (IllegalArgumentException e) {
                throw mismatch(p, "Money amount must be a plain decimal");
            }
        }

        private static MismatchedInputException mismatch(JsonParser p, String message) {
            return MismatchedInputException.from(p, Money.class, message);
        }
    }
}
