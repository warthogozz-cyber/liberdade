package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** Too many lookups are waiting in line for the polite pace; the call gives up without asking the service. */
final class GeocodingBusyException extends DomainException {

    GeocodingBusyException() {
        super(503, "markets.geocoding_busy", "A busca de endereço está ocupada. Tente de novo em instantes.");
    }
}
