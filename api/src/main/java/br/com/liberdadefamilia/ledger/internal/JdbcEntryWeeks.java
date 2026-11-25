package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.EntryWeeks;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcEntryWeeks implements EntryWeeks {

    private final JdbcClient jdbc;

    JdbcEntryWeeks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SortedSet<LocalDate> mondaysWithEntries(UUID workspaceId, UUID membershipId, ZoneId zone) {
        return new TreeSet<>(jdbc.sql("""
                        select distinct date_trunc('week', created_at at time zone :zone)::date
                        from transactions
                        where workspace_id = :workspaceId
                          and created_by = :membershipId
                          and status = 'CONFIRMED'
                          and deleted_at is null
                          and recurring_rule_id is null""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .param("zone", zone.getId())
                .query(LocalDate.class)
                .list());
    }
}
