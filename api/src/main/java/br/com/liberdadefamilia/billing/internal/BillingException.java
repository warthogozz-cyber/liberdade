package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A billing rule said no. The detail is pt-BR and never carries anything from the gateway. */
final class BillingException extends DomainException {

    private BillingException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static BillingException unavailable() {
        return new BillingException(409, "billing.unavailable", "A compra não está disponível agora.");
    }

    static BillingException planActive() {
        return new BillingException(409, "billing.plan-active", "Sua família já tem um plano ativo.");
    }

    static BillingException checkoutFailed() {
        return new BillingException(
                502, "billing.checkout-failed", "Não conseguimos abrir o pagamento agora. Tente de novo em instantes.");
    }
}
