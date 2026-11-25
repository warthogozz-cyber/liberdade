package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;

public record InvestmentOutcome(
        String name, BigDecimal finalBalance, BigDecimal totalContributed, BigDecimal earnings) {}
