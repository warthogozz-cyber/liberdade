package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.ActivityItem;
import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.identity.UserProfile;
import br.com.liberdadefamilia.identity.UserSummary;
import br.com.liberdadefamilia.ledger.ExportedAccount;
import br.com.liberdadefamilia.ledger.ExportedCategory;
import br.com.liberdadefamilia.ledger.ExportedTransaction;
import br.com.liberdadefamilia.ledger.LedgerExport;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.Role;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
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

    private static final int FORMAT_VERSION = 1;
    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");
    private static final List<String> TRANSACTION_HEADER = List.of(
            "data", "tipo", "valor", "moeda", "conta", "conta_destino", "categoria", "descricao", "quem", "criado_em");

    /** The download: its file name and the writer of its bytes. */
    /**
     * The zip, written in full before the controller answers. It is not streamed: a body written from the async
     * executor commits the response, and so saves the session, on a thread of its own while the request thread saves it
     * again, and a JDBC session is not thread-safe (a ConcurrentModificationException, an HTTP 500, now and then). The
     * data is in memory already.
     */
    record Archive(String filename, byte[] content) {}

    private record Family(UUID id, String name) {}

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
            Instant createdAt) {}

    private record Member(UUID id, @Nullable String name, Role role, MembershipStatus status) {}

    private record PersonalData(
            UserProfile profile,
            List<PersonalMembership> memberships,
            List<PersonalConsent> consents,
            List<ActivityItem> activity,
            List<PersonalTransaction> transactions,
            Map<UUID, Map<UUID, String>> names) {}

    private record FamilyData(
            Family family,
            List<Member> members,
            List<ExportedAccount> accounts,
            List<ExportedCategory> categories,
            List<ExportedTransaction> transactions,
            Map<String, Boolean> modules,
            Map<UUID, String> names) {}

    private final UserDirectory users;
    private final Memberships memberships;
    private final Modules modules;
    private final LedgerExport ledger;
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
            AuditTrail audit,
            ConsentService consents,
            JsonMapper json,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.users = users;
        this.memberships = memberships;
        this.modules = modules;
        this.ledger = ledger;
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
        Map<UUID, Map<UUID, String>> names = new HashMap<>();
        for (MemberSummary m : mine) {
            String family = familyNames.getOrDefault(m.workspaceId(), "");
            membershipRows.add(new PersonalMembership(
                    m.membershipId(), m.workspaceId(), family, m.role(), m.nickname(), m.status()));
            names.computeIfAbsent(m.workspaceId(), this::namesOf);
            for (ExportedTransaction t : ledger.transactionsOf(m.workspaceId(), m.membershipId(), m.role())) {
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
                        t.createdAt()));
            }
        }
        transactions.sort(Comparator.comparing(PersonalTransaction::occurredOn)
                .thenComparing(PersonalTransaction::createdAt)
                .thenComparing(PersonalTransaction::id)
                .reversed());

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
        return new PersonalData(profile, membershipRows, consentRows, audit.allActivityOf(userId), transactions, names);
    }

    private FamilyData loadFamily(UUID workspaceId) {
        String name = memberships.workspaceNames(Set.of(workspaceId)).getOrDefault(workspaceId, "");
        Map<UUID, String> names = namesOf(workspaceId);
        List<Member> members = memberships.all(workspaceId).stream()
                .map(m -> new Member(m.membershipId(), names.get(m.membershipId()), m.role(), m.status()))
                .toList();
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (ModuleKey key : ModuleKey.values()) {
            enabled.put(key.wire(), modules.isEnabled(workspaceId, key));
        }
        return new FamilyData(
                new Family(workspaceId, name),
                members,
                ledger.accounts(workspaceId),
                ledger.categories(workspaceId),
                ledger.transactions(workspaceId),
                enabled,
                names);
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
                    t.createdAt()));
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

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("dados.json", json.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        files.put("lancamentos.csv", csv.toBytes(header, rows, Set.of(3)));
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
        root.put("modules", data.modules());

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
                    t.createdAt()));
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
                    archivedAt == null ? "" : archivedAt.toString()));
        }
        Map<UUID, String> categoryNames = new HashMap<>();
        data.categories().forEach(c -> categoryNames.put(c.id(), c.name()));
        List<List<String>> categoryRows = new ArrayList<>();
        for (ExportedCategory c : data.categories()) {
            UUID parent = c.parentId();
            Instant archivedAt = c.archivedAt();
            categoryRows.add(List.of(
                    c.name(),
                    Labels.categoryKind(c.kind()),
                    parent == null ? "" : categoryNames.getOrDefault(parent, ""),
                    archivedAt == null ? "" : archivedAt.toString()));
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
                        List.of("nome", "tipo", "saldo_inicial", "saldo", "moeda", "titular", "arquivada_em"),
                        accountRows,
                        Set.of(2, 3)));
        files.put(
                "categorias.csv", csv.toBytes(List.of("nome", "tipo", "categoria_pai", "arquivada_em"), categoryRows));
        files.put("membros.csv", csv.toBytes(List.of("apelido", "papel", "situacao"), memberRows));
        zip(out, files);
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
            Instant createdAt) {
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
                createdAt.toString());
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
