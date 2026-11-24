package br.com.liberdadefamilia.planning;

import br.com.liberdadefamilia.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A monthly spending limit as the data export carries it. {@code month} is the first day of the month. */
public record ExportedBudget(UUID id, UUID categoryId, LocalDate month, Money limit, Instant createdAt) {}
