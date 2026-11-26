package br.com.liberdadefamilia.markets;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A saved receipt as the data export carries it: when, where and how many items. Never the key or its hash. */
public record ExportedReceipt(
        UUID id, UUID marketId, LocalDate issuedOn, String mode, int itemCount, Instant createdAt) {}
