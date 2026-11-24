package br.com.liberdadefamilia.planning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The projected balance at a month end. Scheduled and estimated spending are accumulated up to that day. */
public record ProjectionPoint(
        LocalDate monthEnd, BigDecimal balance, BigDecimal scheduled, BigDecimal estimatedSpending) {}
