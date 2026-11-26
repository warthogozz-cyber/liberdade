package br.com.liberdadefamilia.markets.internal;

/**
 * Reads the receipts of one state from the state's own page. The 10.2 only defines the type: the read method and its
 * result enter with the first 10.2.x reader, which knows the page.
 */
interface NfceStateReader {

    /** The acronym of the state this reader serves, such as {@code RS}. */
    String uf();
}
