package br.com.liberdadefamilia.markets.internal;

/**
 * An optional hook inside the import transaction. Only tests define a bean of this type; they use it to look at the
 * database from another connection while the swap is half done.
 */
public interface ImportProgressListener {

    /** Called after the old rows are deleted and before the new ones are inserted, inside the transaction. */
    void afterDelete();
}
