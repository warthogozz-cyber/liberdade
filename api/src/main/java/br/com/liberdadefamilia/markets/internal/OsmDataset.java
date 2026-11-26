package br.com.liberdadefamilia.markets.internal;

import java.io.InputStream;

/** Where the weekly extract comes from: the release of this repository, or fixed rows in dev, e2e and tests. */
public interface OsmDataset {

    /** What the publisher says about the file: when the data is from, how many rows, and its SHA-256. */
    OsmManifest manifest() throws OsmImportException;

    /** The {@code .csv.gz} bytes. The caller closes the stream; a download over the size limit fails while reading. */
    InputStream open() throws OsmImportException;
}
