package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.MarketHomeCleared;
import br.com.liberdadefamilia.markets.MarketHomeSet;
import br.com.liberdadefamilia.markets.domain.GeoPoint;
import br.com.liberdadefamilia.markets.internal.MarketViews.HomeRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.HomeView;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The family's address (D9): one point with 3 decimals and an optional name, shared by the whole family. */
@Service
class MarketHomeService {

    static final int DECIMALS = 3;
    static final int LABEL_MAX = 40;

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    MarketHomeService(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional(readOnly = true)
    Optional<HomeView> get(WorkspaceMember member) {
        return jdbc.sql("select lat, lon, label from market_home where workspace_id = :ws")
                .param("ws", member.workspaceId())
                .query((rs, row) -> new HomeView(
                        rs.getBigDecimal("lat").toPlainString(),
                        rs.getBigDecimal("lon").toPlainString(),
                        rs.getString("label")))
                .optional();
    }

    @Transactional
    HomeView set(WorkspaceMember member, HomeRequest request) {
        if (request.lat() == null || request.lon() == null) {
            throw MarketRuleException.positionInvalid();
        }
        GeoPoint point = MarketSearchService.point(request.lat(), request.lon()).requireScale(DECIMALS);
        String label = request.label() == null ? null : request.label().strip();
        if (label != null && label.length() > LABEL_MAX) {
            throw MarketRuleException.labelInvalid();
        }
        if (label != null && label.isEmpty()) {
            label = null;
        }
        GeoPoint stored = point.rounded(DECIMALS);
        jdbc.sql("""
                insert into market_home (workspace_id, lat, lon, label, updated_by, updated_at)
                values (:ws, :lat, :lon, cast(:label as text), :by, now())
                on conflict (workspace_id) do update
                set lat = excluded.lat, lon = excluded.lon, label = excluded.label,
                    updated_by = excluded.updated_by, updated_at = now()
                """)
                .param("ws", member.workspaceId())
                .param("lat", stored.lat())
                .param("lon", stored.lon())
                .param("label", label)
                .param("by", member.membershipId())
                .update();
        events.publishEvent(new MarketHomeSet(member.workspaceId(), member.userId()));
        return new HomeView(stored.lat().toPlainString(), stored.lon().toPlainString(), label);
    }

    @Transactional
    void clear(WorkspaceMember member) {
        int deleted = jdbc.sql("delete from market_home where workspace_id = :ws")
                .param("ws", member.workspaceId())
                .update();
        if (deleted > 0) {
            events.publishEvent(new MarketHomeCleared(member.workspaceId(), member.userId()));
        }
    }
}
