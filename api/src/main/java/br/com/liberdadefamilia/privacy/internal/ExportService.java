package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.ActivityItem;
import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.billing.BillingExport;
import br.com.liberdadefamilia.billing.ExportedOrder;
import br.com.liberdadefamilia.billing.ExportedPlan;
import br.com.liberdadefamilia.gamification.AchievementExport;
import br.com.liberdadefamilia.gamification.ExportedAchievement;
import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.identity.UserProfile;
import br.com.liberdadefamilia.identity.UserSummary;
import br.com.liberdadefamilia.ledger.ExportedAccount;
import br.com.liberdadefamilia.ledger.ExportedCategory;
import br.com.liberdadefamilia.ledger.ExportedTag;
import br.com.liberdadefamilia.ledger.ExportedTransaction;
import br.com.liberdadefamilia.ledger.LedgerExport;
import br.com.liberdadefamilia.markets.ExportedAlias;
import br.com.liberdadefamilia.markets.ExportedDismissal;
import br.com.liberdadefamilia.markets.ExportedListItem;
import br.com.liberdadefamilia.markets.ExportedMarket;
import br.com.liberdadefamilia.markets.ExportedMarketHome;
import br.com.liberdadefamilia.markets.ExportedPrice;
import br.com.liberdadefamilia.markets.ExportedProduct;
import br.com.liberdadefamilia.markets.ExportedReceipt;
import br.com.liberdadefamilia.markets.MarketsExport;
import br.com.liberdadefamilia.planning.ExportedBudget;
import br.com.liberdadefamilia.planning.ExportedContribution;
import br.com.liberdadefamilia.planning.ExportedGoal;
import br.com.liberdadefamilia.planning.ExportedRecurringRule;
import br.com.liberdadefamilia.planning.ExportedReserveSettings;
import br.com.liberdadefamilia.planning.PlanningExport;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import br.com.liberdadefamilia.ventures.ExportedVenture;
import br.com.liberdadefamilia.ventures.ExportedVentureItem;
import br.com.liberdadefamilia.ventures.ExportedVentureProduct;
import br.com.liberdadefamilia.ventures.ExportedVentureStep;
import br.com.liberdadefamilia.ventures.VenturesExport;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceRegion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the data exports (LGPD portability). The data is read in one read-only transaction and the audit entry is
 * written before anything streams, so an export that cannot be audited does not happen. The zip is then written
 * straight to the response from memory, with no temporary file.
 */
@Service
class ExportService {

    private static final int FORMAT_VERSION = 11;
    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");
    private static final List<String> TRANSACTION_HEADER = List.of(
            "data",
            "tipo",
            "valor",
            "moeda",
            "conta",
            "conta_destino",
            "categoria",
            "descricao",
            "quem",
            "criado_em",
            "status",
            "forma_pagamento",
            "tags",
            "projeto");

    /** The download: its file name and the writer of its bytes. */
    /**
     * The zip, written in full before the controller answers. It is not streamed: a body written from the async
     * executor commits the response, and so saves the session, on a thread of its own while the request thread saves it
     * again, and a JDBC session is not thread-safe (a ConcurrentModificationException, an HTTP 500, now and then). The
     * data is in memory already.
     */
    record Archive(String filename, byte[] content) {}

    private record Family(UUID id, String name, String country, String locale) {}

    private record PersonalMembership(
            UUID membershipId,
            UUID workspaceId,
            String familyName,
            Role role,
            @Nullable String nickname,
            MembershipStatus status) {}

    private record PersonalConsent(
            UUID id,
            ConsentPurpose purpose,
            String documentVersion,
            @Nullable UUID workspaceId,
            @Nullable String familyName,
            @Nullable String subjectName,
            @Nullable String grantedByName,
            Instant grantedAt,
            @Nullable Instant revokedAt,
            @Nullable Instant supersededAt) {}

    private record Passkey(Instant createdAt) {}

    private record PersonalTransaction(
            UUID workspaceId,
            String familyName,
            UUID id,
            String kind,
            Money amount,
            LocalDate occurredOn,
            String accountName,
            @Nullable String toAccountName,
            @Nullable String categoryName,
            @Nullable String description,
            UUID memberId,
            @Nullable String who,
            Instant createdAt,
            String status,
            @Nullable PaymentMethod paymentMethod,
            List<String> tags,
            @Nullable LocalDate scheduledOn,
            @Nullable UUID recurringRuleId,
            @Nullable String importKey,
            @Nullable UUID ventureId,
            @JsonIgnore @Nullable String ventureName) {}

    /** A tag the person created, with the family it belongs to. */
    private record PersonalTag(
            UUID workspaceId,
            String familyName,
            UUID id,
            String name,
            Instant createdAt,
            @Nullable Instant archivedAt) {}

    private record Member(UUID id, @Nullable String name, Role role, MembershipStatus status) {}

    /** A recurring rule the person created, with the family it belongs to. */
    private record PersonalRule(
            UUID workspaceId,
            String familyName,
            @JsonUnwrapped ExportedRecurringRule rule) {}

    /** A goal contribution the person made, with the family it belongs to. */
    private record PersonalContribution(
            UUID workspaceId,
            String familyName,
            @JsonUnwrapped ExportedContribution contribution) {}

    /** A project the person created, with the family it belongs to. */
    private record PersonalVenture(
            UUID workspaceId,
            String familyName,
            @JsonUnwrapped ExportedVenture venture) {}

    /** An achievement the person earned, with the family it belongs to. */
    private record PersonalAchievement(UUID workspaceId, String key, Instant earnedAt) {}

    private record PersonalData(
            UserProfile profile,
            List<PersonalMembership> memberships,
            List<PersonalConsent> consents,
            List<ActivityItem> activity,
            List<PersonalTransaction> transactions,
            List<PersonalTag> tags,
            List<PersonalRule> recurringRules,
            List<PersonalContribution> goalContributions,
            List<PersonalVenture> ventures,
            List<PersonalAchievement> achievements,
            Map<UUID, Map<UUID, String>> names) {}

    /** An order as written in the export: the amount as decimal text, like the rest of it. */
    private record OrderLine(
            String product,
            String amount,
            String currency,
            String status,
            Instant createdAt,
            @Nullable Instant paidAt,
            @Nullable Instant refundedAt) {

        static OrderLine of(ExportedOrder o) {
            return new OrderLine(
                    o.product(),
                    BigDecimal.valueOf(o.amountCents(), 2).toPlainString(),
                    o.currency(),
                    o.status(),
                    o.createdAt(),
                    o.paidAt(),
                    o.refundedAt());
        }
    }

    private record FamilyData(
            Family family,
            List<Member> members,
            List<ExportedAccount> accounts,
            List<ExportedCategory> categories,
            List<ExportedTransaction> transactions,
            List<ExportedTag> tags,
            List<ExportedRecurringRule> recurringRules,
            List<ExportedBudget> budgets,
            List<ExportedGoal> goals,
            List<ExportedContribution> goalContributions,
            ExportedReserveSettings reserve,
            List<ExportedVenture> ventures,
            List<ExportedMarket> markets,
            @Nullable ExportedMarketHome marketHome,
            List<ExportedProduct> products,
            List<ExportedAlias> aliases,
            List<ExportedPrice> prices,
            List<ExportedReceipt> receipts,
            List<ExportedListItem> shoppingList,
            List<ExportedDismissal> joinDismissals,
            List<OrderLine> orders,
            @Nullable ExportedPlan plan,
            Map<String, Boolean> modules,
            Map<UUID, String> names) {}

    private final UserDirectory users;
    private final Memberships memberships;
    private final Modules modules;
    private final LedgerExport ledger;
    private final PlanningExport planning;
    private final VenturesExport ventures;
    private final MarketsExport markets;
    private final AchievementExport achievements;
    private final BillingExport billing;
    private final AuditTrail audit;
    private final ConsentService consents;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate readOnly;
    private final CsvWriter csv = new CsvWriter();

    ExportService(
            UserDirectory users,
            Memberships memberships,
            Modules modules,
            LedgerExport ledger,
            PlanningExport planning,
            VenturesExport ventures,
            MarketsExport markets,
            AchievementExport achievements,
            BillingExport billing,
            AuditTrail audit,
            ConsentService consents,
            JsonMapper json,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.users = users;
        this.memberships = memberships;
        this.modules = modules;
        this.ledger = ledger;
        this.planning = planning;
        this.ventures = ventures;
        this.markets = markets;
        this.achievements = achievements;
        this.billing = billing;
        this.audit = audit;
        this.consents = consents;
        this.json = json;
        this.clock = clock;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
        this.readOnly.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** Everything the person has, across every family they are or were in. */
    Archive exportMine(UUID userId) {
        PersonalData data = readOnly.execute(status -> loadMine(userId));
        audit.record(new AuditEntry(null, userId, "data.exported", "USER", userId, Map.of("scope", "ME")));
        Instant now = clock.instant();
        return new Archive("liberdade-meus-dados-" + today() + ".zip", bytes(out -> writeMine(out, data, now)));
    }

    /** Everything the family has. */
    Archive exportFamily(UUID workspaceId, UUID actorUserId) {
        FamilyData data = readOnly.execute(status -> loadFamily(workspaceId));
        audit.record(new AuditEntry(
                workspaceId, actorUserId, "data.exported", "WORKSPACE", workspaceId, Map.of("scope", "FAMILY")));
        Instant now = clock.instant();
        return new Archive("liberdade-familia-" + today() + ".zip", bytes(out -> writeFamily(out, data, now)));
    }

    private static byte[] bytes(StreamingResponseBody writer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writer.writeTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(FORTALEZA));
    }

    // ---- loading

    private PersonalData loadMine(UUID userId) {
        UserProfile profile = users.profileOf(userId);
        List<MemberSummary> mine = memberships.membershipsOf(userId);
        Set<UUID> workspaceIds = new HashSet<>();
        mine.forEach(m -> workspaceIds.add(m.workspaceId()));
        Map<UUID, String> familyNames = memberships.workspaceNames(workspaceIds);

        List<PersonalMembership> membershipRows = new ArrayList<>();
        List<PersonalTransaction> transactions = new ArrayList<>();
        List<PersonalTag> tags = new ArrayList<>();
        List<PersonalRule> rules = new ArrayList<>();
        List<PersonalContribution> contributions = new ArrayList<>();
        List<PersonalVenture> personalVentures = new ArrayList<>();
        List<PersonalAchievement> earned = new ArrayList<>();
        Map<UUID, Map<UUID, String>> names = new HashMap<>();
        for (MemberSummary m : mine) {
            String family = familyNames.getOrDefault(m.workspaceId(), "");
            membershipRows.add(new PersonalMembership(
                    m.membershipId(), m.workspaceId(), family, m.role(), m.nickname(), m.status()));
            names.computeIfAbsent(m.workspaceId(), this::namesOf);
            Map<UUID, String> ventureNames = m.role() == Role.YOUTH ? Map.of() : ventureNamesOf(m.workspaceId());
            for (ExportedTransaction t : ledger.transactionsOf(m.workspaceId(), m.membershipId(), m.role())) {
                String ventureName = t.ventureId() == null ? null : ventureNames.get(t.ventureId());
                transactions.add(new PersonalTransaction(
                        m.workspaceId(),
                        family,
                        t.id(),
                        t.kind(),
                        t.amount(),
                        t.occurredOn(),
                        t.accountName(),
                        t.toAccountName(),
                        t.categoryName(),
                        t.description(),
                        t.memberId(),
                        names.get(m.workspaceId()).get(t.memberId()),
                        t.createdAt(),
                        t.status(),
                        t.paymentMethod(),
                        t.tags(),
                        t.scheduledOn(),
                        t.recurringRuleId(),
                        t.importKey(),
                        ventureName == null ? null : t.ventureId(),
                        ventureName));
            }
            for (ExportedTag t : ledger.tagsCreatedBy(m.workspaceId(), m.membershipId())) {
                tags.add(new PersonalTag(m.workspaceId(), family, t.id(), t.name(), t.createdAt(), t.archivedAt()));
            }
            for (ExportedRecurringRule r : planning.rulesCreatedBy(m.workspaceId(), m.membershipId())) {
                rules.add(new PersonalRule(m.workspaceId(), family, r));
            }
            for (ExportedContribution c : planning.contributionsBy(m.workspaceId(), m.membershipId())) {
                contributions.add(new PersonalContribution(m.workspaceId(), family, c));
            }
            for (ExportedVenture v : ventures.venturesCreatedBy(m.workspaceId(), m.membershipId())) {
                personalVentures.add(new PersonalVenture(m.workspaceId(), family, v));
            }
            for (ExportedAchievement a : achievements.of(m.workspaceId(), m.membershipId())) {
                earned.add(new PersonalAchievement(m.workspaceId(), a.key(), a.earnedAt()));
            }
        }
        transactions.sort(Comparator.comparing(PersonalTransaction::occurredOn)
                .thenComparing(PersonalTransaction::createdAt)
                .thenComparing(PersonalTransaction::id)
                .reversed());
        tags.sort(Comparator.comparing(PersonalTag::familyName)
                .thenComparing(PersonalTag::name)
                .thenComparing(PersonalTag::id));
        rules.sort(Comparator.comparing(PersonalRule::familyName)
                .thenComparing(r -> r.rule().startOn())
                .thenComparing(r -> r.rule().id()));

        contributions.sort(Comparator.comparing(PersonalContribution::familyName)
                .thenComparing(c -> c.contribution().occurredOn())
                .thenComparing(c -> c.contribution().id()));

        List<PersonalConsent> consentRows = consents.listForExport(userId).stream()
                .map(c -> new PersonalConsent(
                        c.id(),
                        c.purpose(),
                        c.documentVersion(),
                        c.workspaceId(),
                        c.workspaceName(),
                        c.subjectName(),
                        c.grantedByName(),
                        c.grantedAt(),
                        c.revokedAt(),
                        c.supersededAt()))
                .toList();
        return new PersonalData(
                profile,
                membershipRows,
                consentRows,
                audit.allActivityOf(userId),
                transactions,
                tags,
                rules,
                contributions,
                personalVentures,
                earned,
                names);
    }

    private FamilyData loadFamily(UUID workspaceId) {
        String name = memberships.workspaceNames(Set.of(workspaceId)).getOrDefault(workspaceId, "");
        WorkspaceRegion region = memberships.regionOf(workspaceId).orElseThrow();
        Map<UUID, String> names = namesOf(workspaceId);
        List<Member> members = memberships.all(workspaceId).stream()
                .map(m -> new Member(m.membershipId(), names.get(m.membershipId()), m.role(), m.status()))
                .toList();
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (ModuleKey key : ModuleKey.values()) {
            enabled.put(key.wire(), modules.isEnabled(workspaceId, key));
        }
        return new FamilyData(
                new Family(workspaceId, name, region.country(), region.locale()),
                members,
                ledger.accounts(workspaceId),
                ledger.categories(workspaceId),
                ledger.transactions(workspaceId),
                ledger.tags(workspaceId),
                planning.rules(workspaceId),
                planning.budgets(workspaceId),
                planning.goals(workspaceId),
                planning.contributions(workspaceId),
                planning.reserveSettings(workspaceId),
                ventures.ventures(workspaceId),
                markets.familyMarkets(workspaceId),
                markets.home(workspaceId).orElse(null),
                markets.products(workspaceId),
                markets.aliases(workspaceId),
                markets.prices(workspaceId),
                markets.receipts(workspaceId),
                markets.shoppingList(workspaceId),
                markets.joinDismissals(workspaceId),
                billing.orders(workspaceId).stream().map(OrderLine::of).toList(),
                billing.plan(workspaceId).orElse(null),
                enabled,
                names);
    }

    /** The name of each of the family's ventures. */
    private Map<UUID, String> ventureNamesOf(UUID workspaceId) {
        Map<UUID, String> names = new HashMap<>();
        ventures.ventures(workspaceId).forEach(v -> names.put(v.id(), v.name()));
        return names;
    }

    /** Each membership's display name: the family nickname, else the person's name; absent once the account is gone. */
    private Map<UUID, String> namesOf(UUID workspaceId) {
        List<MemberSummary> all = memberships.all(workspaceId);
        Set<UUID> userIds = new HashSet<>();
        all.forEach(m -> {
            if (m.userId() != null) {
                userIds.add(m.userId());
            }
        });
        Map<UUID, UserSummary> people = users.findAll(userIds);
        Map<UUID, String> names = new HashMap<>();
        for (MemberSummary m : all) {
            String nickname = m.nickname();
            UUID userId = m.userId();
            if (nickname != null && !nickname.isBlank()) {
                names.put(m.membershipId(), nickname);
            } else if (userId != null && people.containsKey(userId)) {
                names.put(m.membershipId(), people.get(userId).displayName());
            }
        }
        return names;
    }

    // ---- writing

    private void writeMine(OutputStream out, PersonalData data, Instant now) throws IOException {
        UserProfile p = data.profile();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("displayName", p.displayName());
        profile.put("email", p.email());
        profile.put("locale", p.locale());
        profile.put("createdAt", p.createdAt());
        Instant passkeyCreatedAt = p.passkeyCreatedAt();
        List<Passkey> passkeys = passkeyCreatedAt == null ? List.of() : List.of(new Passkey(passkeyCreatedAt));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("exportedAt", now);
        root.put("profile", profile);
        root.put("memberships", data.memberships());
        root.put("consents", data.consents());
        root.put("passkeys", passkeys);
        root.put("activity", data.activity());
        root.put("transactions", data.transactions());
        root.put("tags", data.tags());
        root.put("recurringRules", data.recurringRules());
        root.put("goalContributions", data.goalContributions());
        root.put("ventures", data.ventures());
        root.put("achievements", data.achievements());

        List<List<String>> rows = new ArrayList<>();
        for (PersonalTransaction t : data.transactions()) {
            List<String> row = new ArrayList<>();
            row.add(t.familyName());
            row.addAll(transactionCells(
                    t.kind(),
                    t.occurredOn(),
                    t.amount(),
                    t.accountName(),
                    t.toAccountName(),
                    t.categoryName(),
                    t.description(),
                    t.who(),
                    t.createdAt(),
                    t.status(),
                    t.paymentMethod(),
                    t.tags(),
                    t.ventureName()));
            rows.add(row);
        }
        List<String> header = new ArrayList<>(List.of("familia"));
        header.addAll(TRANSACTION_HEADER);

        List<List<String>> activityRows = new ArrayList<>();
        for (ActivityItem a : data.activity()) {
            activityRows.add(List.of(
                    a.occurredAt().toString(),
                    a.action(),
                    orEmpty(a.actorName()),
                    a.metadata().getOrDefault("browser", ""),
                    a.metadata().getOrDefault("os", "")));
        }

        List<List<String>> contributionRows = new ArrayList<>();
        for (PersonalContribution pc : data.goalContributions()) {
            ExportedContribution c = pc.contribution();
            contributionRows.add(List.of(
                    pc.familyName(),
                    c.goalName(),
                    c.occurredOn().toString(),
                    CsvWriter.amount(c.amount().amount()),
                    c.amount().currency().getCurrencyCode()));
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("dados.json", json.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        files.put("lancamentos.csv", csv.toBytes(header, rows, Set.of(3)));
        files.put(
                "aportes.csv",
                csv.toBytes(List.of("familia", "meta", "data", "valor", "moeda"), contributionRows, Set.of(3)));
        files.put(
                "atividade.csv", csv.toBytes(List.of("quando", "acao", "quem", "navegador", "sistema"), activityRows));
        zip(out, files);
    }

    private void writeFamily(OutputStream out, FamilyData data, Instant now) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("exportedAt", now);
        root.put("workspace", data.family());
        root.put("members", data.members());
        root.put("accounts", data.accounts());
        root.put("categories", data.categories());
        root.put("transactions", data.transactions());
        root.put("recurringRules", data.recurringRules());
        root.put("tags", data.tags());
        root.put("budgets", data.budgets());
        root.put("goals", data.goals());
        root.put("goalContributions", data.goalContributions());
        root.put("reserveTargetMonths", data.reserve().targetMonths());
        root.put("manualMonthlyCost", data.reserve().manualMonthlyCost());
        root.put("ventures", data.ventures());
        root.put("markets", data.markets());
        root.put("marketHome", data.marketHome());
        root.put("products", data.products());
        root.put("aliases", data.aliases());
        root.put("prices", data.prices());
        root.put("receipts", data.receipts());
        root.put("shoppingList", data.shoppingList());
        root.put("joinDismissals", data.joinDismissals());
        root.put("modules", data.modules());
        root.put("orders", data.orders());
        root.put("plan", data.plan());

        Map<UUID, String> ventureNames = new HashMap<>();
        data.ventures().forEach(v -> ventureNames.put(v.id(), v.name()));
        List<List<String>> transactionRows = new ArrayList<>();
        for (ExportedTransaction t : data.transactions()) {
            transactionRows.add(transactionCells(
                    t.kind(),
                    t.occurredOn(),
                    t.amount(),
                    t.accountName(),
                    t.toAccountName(),
                    t.categoryName(),
                    t.description(),
                    data.names().get(t.memberId()),
                    t.createdAt(),
                    t.status(),
                    t.paymentMethod(),
                    t.tags(),
                    t.ventureId() == null ? null : ventureNames.get(t.ventureId())));
        }
        List<List<String>> accountRows = new ArrayList<>();
        for (ExportedAccount a : data.accounts()) {
            UUID owner = a.ownerMembershipId();
            Instant archivedAt = a.archivedAt();
            accountRows.add(List.of(
                    a.name(),
                    Labels.accountType(a.type()),
                    CsvWriter.amount(a.initialBalance().amount()),
                    CsvWriter.amount(a.balance().amount()),
                    a.balance().currency().getCurrencyCode(),
                    owner == null ? "" : orEmpty(data.names().get(owner)),
                    archivedAt == null ? "" : archivedAt.toString(),
                    Boolean.toString(a.isReserve())));
        }
        Map<UUID, String> categoryNames = new HashMap<>();
        data.categories().forEach(c -> categoryNames.put(c.id(), c.name()));
        Map<UUID, String> accountNames = new HashMap<>();
        data.accounts().forEach(a -> accountNames.put(a.id(), a.name()));
        List<List<String>> ruleRows = new ArrayList<>();
        for (ExportedRecurringRule r : data.recurringRules()) {
            UUID category = r.categoryId();
            UUID toAccount = r.toAccountId();
            LocalDate endOn = r.endOn();
            Instant archivedAt = r.archivedAt();
            ruleRows.add(List.of(
                    Labels.transactionKind(r.kind()),
                    CsvWriter.amount(r.amount().amount()),
                    r.amount().currency().getCurrencyCode(),
                    accountNames.getOrDefault(r.accountId(), ""),
                    toAccount == null ? "" : accountNames.getOrDefault(toAccount, ""),
                    category == null ? "" : categoryNames.getOrDefault(category, ""),
                    Labels.paymentMethod(r.paymentMethod()),
                    orEmpty(r.description()),
                    Labels.frequency(r.frequency()),
                    Integer.toString(r.interval()),
                    r.startOn().toString(),
                    endOn == null ? "" : endOn.toString(),
                    archivedAt == null ? "" : archivedAt.toString()));
        }
        List<List<String>> tagRows = new ArrayList<>();
        for (ExportedTag t : data.tags()) {
            Instant archivedAt = t.archivedAt();
            tagRows.add(List.of(t.name(), archivedAt == null ? "" : archivedAt.toString()));
        }
        List<List<String>> categoryRows = new ArrayList<>();
        for (ExportedCategory c : data.categories()) {
            UUID parent = c.parentId();
            Instant archivedAt = c.archivedAt();
            categoryRows.add(List.of(
                    c.name(),
                    Labels.categoryKind(c.kind()),
                    parent == null ? "" : categoryNames.getOrDefault(parent, ""),
                    archivedAt == null ? "" : archivedAt.toString(),
                    Boolean.toString(c.isFixed())));
        }
        List<List<String>> budgetRows = new ArrayList<>();
        for (ExportedBudget b : data.budgets()) {
            budgetRows.add(List.of(
                    categoryNames.getOrDefault(b.categoryId(), ""),
                    b.month().toString().substring(0, 7),
                    CsvWriter.amount(b.limit().amount()),
                    b.limit().currency().getCurrencyCode()));
        }
        List<List<String>> goalRows = new ArrayList<>();
        for (ExportedGoal g : data.goals()) {
            LocalDate dueOn = g.dueOn();
            UUID linked = g.linkedAccountId();
            Instant achievedAt = g.achievedAt();
            Instant archivedAt = g.archivedAt();
            goalRows.add(List.of(
                    g.name(),
                    CsvWriter.amount(g.target().amount()),
                    g.target().currency().getCurrencyCode(),
                    dueOn == null ? "" : dueOn.toString().substring(0, 7),
                    g.icon(),
                    linked == null ? "" : accountNames.getOrDefault(linked, ""),
                    achievedAt == null ? "" : achievedAt.toString(),
                    archivedAt == null ? "" : archivedAt.toString()));
        }
        List<List<String>> contributionRows = new ArrayList<>();
        for (ExportedContribution c : data.goalContributions()) {
            UUID member = c.memberId();
            contributionRows.add(List.of(
                    c.goalName(),
                    c.occurredOn().toString(),
                    CsvWriter.amount(c.amount().amount()),
                    c.amount().currency().getCurrencyCode(),
                    member == null ? "" : orEmpty(data.names().get(member))));
        }
        List<List<String>> marketRows = new ArrayList<>();
        for (ExportedMarket m : data.markets()) {
            LocalDate validUntil = m.flyerValidUntil();
            Instant archivedAt = m.archivedAt();
            marketRows.add(List.of(
                    m.id().toString(),
                    m.origin(),
                    orEmpty(m.osmRef()),
                    orEmpty(m.name()),
                    m.kind() == null ? "" : Labels.marketKind(m.kind()),
                    coordinate(m.lat()),
                    coordinate(m.lon()),
                    orEmpty(m.cnpj()),
                    orEmpty(m.website()),
                    orEmpty(m.instagram()),
                    orEmpty(m.flyerUrl()),
                    orEmpty(m.flyerSource()),
                    validUntil == null ? "" : validUntil.toString(),
                    archivedAt == null ? "" : archivedAt.toString()));
        }
        List<List<String>> homeRows = new ArrayList<>();
        ExportedMarketHome home = data.marketHome();
        if (home != null) {
            homeRows.add(List.of(coordinate(home.lat()), coordinate(home.lon()), orEmpty(home.label())));
        }
        List<List<String>> productRows = new ArrayList<>();
        for (ExportedProduct p : data.products()) {
            productRows.add(List.of(
                    p.id().toString(),
                    p.name(),
                    p.unit(),
                    orEmpty(p.gtin()),
                    p.createdAt().toString()));
        }
        List<List<String>> aliasRows = new ArrayList<>();
        for (ExportedAlias a : data.aliases()) {
            aliasRows.add(List.of(
                    a.marketId().toString(), a.descriptionNorm(), a.productId().toString()));
        }
        List<List<String>> priceRows = new ArrayList<>();
        for (ExportedPrice p : data.prices()) {
            UUID receiptId = p.receiptId();
            BigDecimal quantity = p.quantity();
            priceRows.add(List.of(
                    p.productId().toString(),
                    p.marketId().toString(),
                    receiptId == null ? "" : receiptId.toString(),
                    p.observedOn().toString(),
                    p.unit(),
                    quantity == null ? "" : fourDecimals(quantity),
                    fourDecimals(p.unitPrice())));
        }
        List<List<String>> receiptRows = new ArrayList<>();
        for (ExportedReceipt r : data.receipts()) {
            receiptRows.add(List.of(
                    r.id().toString(),
                    r.marketId().toString(),
                    r.issuedOn().toString(),
                    r.mode(),
                    Integer.toString(r.itemCount()),
                    r.createdAt().toString()));
        }
        List<List<String>> listRows = new ArrayList<>();
        for (ExportedListItem i : data.shoppingList()) {
            Instant checkedAt = i.checkedAt();
            listRows.add(List.of(
                    i.productId().toString(),
                    fourDecimals(i.quantity()),
                    checkedAt == null ? "" : checkedAt.toString(),
                    Integer.toString(i.position()),
                    i.createdAt().toString()));
        }
        List<List<String>> dismissalRows = new ArrayList<>();
        for (ExportedDismissal d : data.joinDismissals()) {
            dismissalRows.add(List.of(d.productA().toString(), d.productB().toString()));
        }
        List<List<String>> ventureRows = new ArrayList<>();
        List<List<String>> ventureItemRows = new ArrayList<>();
        List<List<String>> ventureProductRows = new ArrayList<>();
        List<List<String>> ventureStepRows = new ArrayList<>();
        for (ExportedVenture v : data.ventures()) {
            LocalDate startedOn = v.startedOn();
            LocalDate closedOn = v.closedOn();
            ventureRows.add(List.of(
                    v.name(),
                    Labels.ventureType(v.type()),
                    Labels.ventureStatus(v.status()),
                    startedOn == null ? "" : startedOn.toString(),
                    closedOn == null ? "" : closedOn.toString(),
                    v.currency()));
            for (ExportedVentureItem i : v.items()) {
                ventureItemRows.add(List.of(
                        v.name(),
                        Labels.ventureItemKind(i.kind()),
                        i.name(),
                        CsvWriter.amount(i.amount().amount()),
                        i.amount().currency().getCurrencyCode()));
            }
            for (ExportedVentureProduct p : v.products()) {
                ventureProductRows.add(List.of(
                        v.name(),
                        p.name(),
                        CsvWriter.amount(p.unitVariableCost().amount()),
                        CsvWriter.amount(p.unitPrice().amount()),
                        Integer.toString(p.plannedUnitsMonth()),
                        p.unitPrice().currency().getCurrencyCode()));
            }
            for (ExportedVentureStep s : v.steps()) {
                Instant doneAt = s.doneAt();
                ventureStepRows.add(List.of(
                        v.name(),
                        Integer.toString(s.position() + 1),
                        s.title(),
                        doneAt == null ? "" : doneAt.toString()));
            }
        }
        List<List<String>> memberRows = new ArrayList<>();
        for (Member m : data.members()) {
            memberRows.add(List.of(orEmpty(m.name()), Labels.role(m.role()), Labels.status(m.status())));
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("dados.json", json.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        files.put("lancamentos.csv", csv.toBytes(TRANSACTION_HEADER, transactionRows, Set.of(2)));
        files.put(
                "contas.csv",
                csv.toBytes(
                        List.of(
                                "nome",
                                "tipo",
                                "saldo_inicial",
                                "saldo",
                                "moeda",
                                "titular",
                                "arquivada_em",
                                "reserva"),
                        accountRows,
                        Set.of(2, 3)));
        files.put(
                "categorias.csv",
                csv.toBytes(List.of("nome", "tipo", "categoria_pai", "arquivada_em", "fixa"), categoryRows));
        files.put("membros.csv", csv.toBytes(List.of("apelido", "papel", "situacao"), memberRows));
        files.put(
                "recorrencias.csv",
                csv.toBytes(
                        List.of(
                                "tipo",
                                "valor",
                                "moeda",
                                "conta",
                                "conta_destino",
                                "categoria",
                                "forma_pagamento",
                                "descricao",
                                "frequencia",
                                "intervalo",
                                "inicio",
                                "fim",
                                "arquivada_em"),
                        ruleRows,
                        Set.of(1)));
        files.put("tags.csv", csv.toBytes(List.of("nome", "arquivada_em"), tagRows));
        files.put("orcamentos.csv", csv.toBytes(List.of("categoria", "mes", "limite", "moeda"), budgetRows, Set.of(2)));
        files.put(
                "metas.csv",
                csv.toBytes(
                        List.of(
                                "nome",
                                "valor_alvo",
                                "moeda",
                                "vencimento",
                                "icone",
                                "conta_vinculada",
                                "atingida_em",
                                "arquivada_em"),
                        goalRows,
                        Set.of(1)));
        files.put(
                "aportes.csv",
                csv.toBytes(List.of("meta", "data", "valor", "moeda", "quem"), contributionRows, Set.of(2)));
        files.put(
                "projetos.csv",
                csv.toBytes(List.of("nome", "tipo", "situacao", "inicio", "encerramento", "moeda"), ventureRows));
        files.put(
                "projetos_itens.csv",
                csv.toBytes(List.of("projeto", "tipo", "nome", "valor", "moeda"), ventureItemRows, Set.of(3)));
        files.put(
                "projetos_produtos.csv",
                csv.toBytes(
                        List.of("projeto", "nome", "custo_unitario", "preco", "venda_prevista_mes", "moeda"),
                        ventureProductRows,
                        Set.of(2, 3)));
        files.put(
                "projetos_passos.csv",
                csv.toBytes(List.of("projeto", "posicao", "passo", "feito_em"), ventureStepRows));
        files.put(
                "feira-mercados.csv",
                csv.toBytes(
                        List.of(
                                "id",
                                "origem",
                                "ref_osm",
                                "nome",
                                "tipo",
                                "lat",
                                "lon",
                                "cnpj",
                                "site",
                                "instagram",
                                "encarte",
                                "fonte_encarte",
                                "encarte_valido_ate",
                                "arquivado_em"),
                        marketRows,
                        Set.of(5, 6)));
        files.put("feira-endereco.csv", csv.toBytes(List.of("lat", "lon", "rotulo"), homeRows, Set.of(0, 1)));
        files.put(
                "feira-produtos.csv", csv.toBytes(List.of("id", "nome", "unidade", "gtin", "criado_em"), productRows));
        files.put(
                "feira-apelidos.csv",
                csv.toBytes(List.of("mercado_id", "descricao_normalizada", "produto_id"), aliasRows));
        files.put(
                "feira-precos.csv",
                csv.toBytes(
                        List.of(
                                "produto_id",
                                "mercado_id",
                                "nota_id",
                                "data",
                                "unidade",
                                "quantidade",
                                "preco_unitario"),
                        priceRows,
                        Set.of(5, 6)));
        files.put(
                "feira-notas.csv",
                csv.toBytes(List.of("id", "mercado_id", "data", "modo", "itens", "criado_em"), receiptRows));
        files.put(
                "feira-lista.csv",
                csv.toBytes(
                        List.of("produto_id", "quantidade", "pego_em", "posicao", "criado_em"), listRows, Set.of(1)));
        files.put("feira-nao-juntar.csv", csv.toBytes(List.of("produto_a", "produto_b"), dismissalRows));
        zip(out, files);
    }

    /** The point as it is stored, with a comma: {@code -23,00300}. It is a number, so no formula guard applies. */
    private static String coordinate(@Nullable BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }

    /** {@code 4,9900}: four decimals and a comma, as the price is stored. */
    private static String fourDecimals(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_EVEN).toPlainString().replace('.', ',');
    }

    private static List<String> transactionCells(
            String kind,
            LocalDate occurredOn,
            Money amount,
            String account,
            @Nullable String toAccount,
            @Nullable String category,
            @Nullable String description,
            @Nullable String who,
            Instant createdAt,
            String status,
            @Nullable PaymentMethod paymentMethod,
            List<String> tags,
            @Nullable String ventureName) {
        return List.of(
                occurredOn.toString(),
                Labels.transactionKind(kind),
                CsvWriter.amount(amount.amount()),
                amount.currency().getCurrencyCode(),
                account,
                orEmpty(toAccount),
                orEmpty(category),
                orEmpty(description),
                orEmpty(who),
                createdAt.toString(),
                Labels.transactionStatus(status),
                Labels.paymentMethod(paymentMethod),
                String.join("; ", tags),
                orEmpty(ventureName));
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /**
     * Writes the finished files as a zip. Everything is already in memory, so a failure while building never reaches
     * the response; the response stream itself is left open for the container to close.
     */
    private static void zip(OutputStream out, Map<String, byte[]> files) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(StreamUtils.nonClosing(out))) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
    }

    /** The Portuguese words the spreadsheets use, matching the web app. */
    private static final class Labels {

        static String accountType(String type) {
            return switch (type) {
                case "CHECKING" -> "Conta corrente";
                case "SAVINGS" -> "Poupança";
                case "CASH" -> "Dinheiro";
                case "CREDIT_CARD" -> "Cartão de crédito";
                case "WALLET" -> "Carteira digital";
                case "INVESTMENT" -> "Investimento";
                default -> type;
            };
        }

        static String transactionKind(String kind) {
            return switch (kind) {
                case "EXPENSE" -> "Gasto";
                case "INCOME" -> "Receita";
                case "TRANSFER" -> "Transferência";
                default -> kind;
            };
        }

        static String transactionStatus(String status) {
            return "PLANNED".equals(status) ? "Previsto" : "Confirmado";
        }

        static String paymentMethod(@Nullable PaymentMethod method) {
            if (method == null) {
                return "";
            }
            return switch (method) {
                case PIX -> "Pix";
                case CREDIT_CARD -> "Crédito";
                case DEBIT_CARD -> "Débito";
                case BOLETO -> "Boleto";
                case BANK_TRANSFER -> "TED";
                case CASH -> "Dinheiro";
            };
        }

        static String frequency(String frequency) {
            return switch (frequency) {
                case "WEEKLY" -> "Semanal";
                case "MONTHLY" -> "Mensal";
                case "YEARLY" -> "Anual";
                default -> frequency;
            };
        }

        static String ventureType(String type) {
            return switch (type) {
                case "FOOD" -> "Culinária";
                case "DIGITAL_PRODUCT" -> "Produto digital";
                case "CONTENT" -> "Conteúdo";
                case "RESALE" -> "Revenda";
                case "SERVICE" -> "Serviço";
                case "OTHER" -> "Outro";
                default -> type;
            };
        }

        static String ventureStatus(String status) {
            return switch (status) {
                case "PLANNING" -> "Planejando";
                case "ACTIVE" -> "Ativo";
                case "PAUSED" -> "Pausado";
                case "CLOSED" -> "Encerrado";
                default -> status;
            };
        }

        static String ventureItemKind(String kind) {
            return "INITIAL".equals(kind) ? "Inicial" : "Fixo mensal";
        }

        static String marketKind(String kind) {
            return switch (kind) {
                case "SUPERMARKET" -> "Supermercado";
                case "CONVENIENCE" -> "Conveniência";
                case "GREENGROCER" -> "Hortifruti";
                case "MARKETPLACE" -> "Feira";
                default -> kind;
            };
        }

        static String categoryKind(String kind) {
            return "INCOME".equals(kind) ? "Receita" : "Gasto";
        }

        static String role(Role role) {
            return switch (role) {
                case OWNER -> "Dono";
                case ADULT -> "Adulto";
                case YOUTH -> "Jovem";
                case VIEWER -> "Leitor";
            };
        }

        static String status(MembershipStatus status) {
            return status == MembershipStatus.ACTIVE ? "Ativo" : "Ex-membro";
        }
    }
}
