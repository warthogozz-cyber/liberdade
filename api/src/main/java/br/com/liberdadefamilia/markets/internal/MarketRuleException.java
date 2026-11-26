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

    static MarketRuleException labelInvalid() {
        return new MarketRuleException(422, "markets.label_invalid", "O nome do endereço pode ter até 40 caracteres.");
    }

    static MarketRuleException geocodingOff() {
        return new MarketRuleException(
                503, "markets.geocoding_off", "A busca de endereço está desligada. Marque o ponto no mapa.");
    }

    static MarketRuleException geocodingLimit() {
        return new MarketRuleException(
                429, "markets.geocoding_limit", "Você já fez muitas buscas de endereço hoje. Tente amanhã.");
    }

    static MarketRuleException geocodingFailed() {
        return new MarketRuleException(
                502, "markets.geocoding_failed", "Não foi possível buscar o endereço agora. Tente mais tarde.");
    }

    static MarketRuleException queryInvalid() {
        return new MarketRuleException(
                422, "markets.query_invalid", "Escreva de 1 a 120 caracteres para buscar o endereço.");
    }

    static MarketRuleException cnpjTaken() {
        return new MarketRuleException(
                409, "market.cnpj_taken", "Este CNPJ já está ligado a outro mercado da família.");
    }

    static MarketRuleException notLeft() {
        return new MarketRuleException(409, "market.not_left", "Este mercado ainda está no OpenStreetMap.");
    }
}
