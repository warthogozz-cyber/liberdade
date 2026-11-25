package br.com.liberdadefamilia.markets.internal;

/** "Ver aqui" cannot show this link: the switch is off, the link is not embeddable or Instagram did not answer. */
public final class InstagramUnavailableException extends RuntimeException {

    public InstagramUnavailableException() {
        super("the instagram embed is unavailable");
    }
}
