package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The search radius is outside 1 to 10 km. */
final class RadiusInvalidException extends DomainException {

    RadiusInvalidException() {
        super(422, "markets.radius_invalid", "O raio da busca vai de 1 a 10 km.");
    }
}
