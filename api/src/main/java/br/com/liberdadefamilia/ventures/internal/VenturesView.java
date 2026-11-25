package br.com.liberdadefamilia.ventures.internal;

import java.util.List;

/** The family's projects, and whether one more would pass the plan limit. Always false for the reader. */
record VenturesView(List<VentureSummary> ventures, boolean limitReached) {}
