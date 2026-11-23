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

    static BillingException orderNotFound() {
        return new BillingException(404, "billing.order-not-found", "Não encontramos esse pedido.");
    }

    static BillingException refundWindowClosed() {
        return new BillingException(
                409, "billing.refund-window-closed", "O prazo de 7 dias para o reembolso já passou.");
    }

    static BillingException refundNotAllowed() {
        return new BillingException(409, "billing.refund-not-allowed", "Esse pedido não pode ser reembolsado.");
    }

    static BillingException checkoutFailed() {
        return new BillingException(
                502, "billing.checkout-failed", "Não conseguimos abrir o pagamento agora. Tente de novo em instantes.");
    }

    static BillingException webhookUnauthorized() {
        return new BillingException(401, "billing.webhook-unauthorized", "Requisição não autorizada.");
    }

    static BillingException requestTooLarge() {
        return new BillingException(413, "request.too-large", "A requisição é grande demais.");
    }

    static BillingException webhookInvalid() {
        return new BillingException(400, "request.invalid", "Requisição inválida.");
    }
}
