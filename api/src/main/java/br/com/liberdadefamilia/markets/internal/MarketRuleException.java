package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A rule of the Feira's writes that the request broke. The message never carries the value that broke it. */
final class MarketRuleException extends DomainException {

    private MarketRuleException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static MarketRuleException positionInvalid() {
        return new MarketRuleException(
                422, "markets.position_invalid", "Informe latitude e longitude juntas, ou nenhuma das duas.");
    }

    static MarketRuleException linkInvalid() {
        return new MarketRuleException(
                422, "markets.link_invalid", "O link precisa começar com https:// e ter até 500 caracteres.");
    }

    static MarketRuleException instagramInvalid() {
        return new MarketRuleException(
                422, "markets.instagram_invalid", "Informe um perfil (@perfil) ou um link de post do Instagram.");
    }

    static MarketRuleException flyerSourceRequired() {
        return new MarketRuleException(
                422,
                "markets.flyer_source_required",
                "Diga de onde veio o encarte (por exemplo, \"site do mercado\").");
    }

    static MarketRuleException notFamily() {
        return new MarketRuleException(
                409, "market.not_family", "Só mercados adicionados pela família têm posição própria.");
    }

    static MarketRuleException notLeft() {
        return new MarketRuleException(409, "market.not_left", "Este mercado ainda está no OpenStreetMap.");
    }
}
