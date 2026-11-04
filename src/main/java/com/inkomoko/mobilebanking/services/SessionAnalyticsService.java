package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.controllers.UssdController;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.repositories.UssdSessionRepository;
import com.inkomoko.mobilebanking.utils.menus.CommonMenus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * USSD session log and visit analytics. Sessions are billed per session, so the per-phone
 * counts and daily totals here are what the monthly USSD invoice is checked against.
 */
@Service
@RequiredArgsConstructor
public class SessionAnalyticsService {

    /** Session outcome shown in the portal; {@link #IN_PROGRESS} is a session still open. */
    public static final String IN_PROGRESS = "IN_PROGRESS";

    private static final Map<String, String> SERVICE_LABELS = Map.of(
            "loan", "Loan", "repay", "Repay loan", "account", "Account info", "settings", "Settings",
            "help", "Help", "statement", "Mini statement", "balance", "Balance", "status", "Loan status");

    private final UssdSessionRepository repo;

    public record SessionView(Long id, String sessionId, String msisdn, String clientName, String networkCode,
                              String language, String status, String lastScreen, boolean authenticated,
                              int screens, long durationSeconds, LocalDateTime startedAt, LocalDateTime endedAt) {
    }

    public record MsisdnVisits(String msisdn, String clientName, long sessions, long completed, long leftOrTimedOut,
                               long notRegistered, long other, long screens, LocalDateTime firstVisit,
                               LocalDateTime lastVisit) {
    }

    public record NetworkCount(String network, long sessions, long phones) {
    }

    public record DayCount(LocalDate day, long sessions, long phones) {
    }

    public record SessionSummary(LocalDateTime from, LocalDateTime to, long sessions, long uniquePhones,
                                 long screens, Map<String, Long> byStatus, List<NetworkCount> byNetwork,
                                 List<DayCount> byDay) {
    }

    public SessionSummary summary(LocalDateTime from, LocalDateTime to) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : repo.countByOutcome(from, to)) {
            String status = status((ESessionEndReason) row[0], Boolean.TRUE.equals(row[1]));
            long n = ((Number) row[2]).longValue();
            byStatus.merge(status, n, Long::sum);
            total += n;
        }
        List<NetworkCount> networks = repo.countByNetwork(from, to).stream()
                .map(r -> new NetworkCount(r[0] == null ? "UNKNOWN" : r[0].toString(),
                        ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .toList();
        List<DayCount> days = repo.countByDay(from, to).stream()
                .map(r -> new DayCount(toLocalDate(r[0]), ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .toList();
        return new SessionSummary(from, to, total, repo.countDistinctMsisdn(from, to), repo.sumRequests(from, to),
                byStatus, networks, days);
    }

    public Page<SessionView> sessions(LocalDateTime from, LocalDateTime to, String msisdn, String status, Pageable page) {
        Pageable sorted = PageRequest.of(page.getPageNumber(), page.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return repo.findAll(filter(from, to, msisdn, status), sorted).map(SessionAnalyticsService::view);
    }

    public Page<MsisdnVisits> visitsByMsisdn(LocalDateTime from, LocalDateTime to, String msisdn, Pageable page) {
        return repo.visitsByMsisdn(from, to, likePattern(msisdn), page).map(r -> {
            long sessions = ((Number) r[2]).longValue();
            long completed = ((Number) r[3]).longValue();
            long left = ((Number) r[4]).longValue();
            long notRegistered = ((Number) r[5]).longValue();
            return new MsisdnVisits((String) r[0], (String) r[1], sessions, completed, left, notRegistered,
                    sessions - completed - left - notRegistered, ((Number) r[6]).longValue(),
                    (LocalDateTime) r[7], (LocalDateTime) r[8]);
        });
    }

    // ── CSV ────────────────────────────────────────────────────────────────

    public String sessionsCsv(LocalDateTime from, LocalDateTime to, String msisdn, String status, int maxRows) {
        StringBuilder csv = new StringBuilder("started_at,ended_at,msisdn,client_name,network,language,status,"
                + "last_screen,signed_in,screens,duration_seconds,session_id\n");
        for (SessionView v : sessions(from, to, msisdn, status, PageRequest.of(0, maxRows))) {
            row(csv, v.startedAt(), v.endedAt(), v.msisdn(), v.clientName(), v.networkCode(), v.language(), v.status(),
                    v.lastScreen(), v.authenticated() ? "yes" : "no", v.screens(), v.durationSeconds(), v.sessionId());
        }
        return csv.toString();
    }

    public String visitsCsv(LocalDateTime from, LocalDateTime to, String msisdn, int maxRows) {
        StringBuilder csv = new StringBuilder("msisdn,client_name,sessions,completed,left_or_timed_out,"
                + "not_registered,other,screens,first_visit,last_visit\n");
        for (MsisdnVisits v : visitsByMsisdn(from, to, msisdn, PageRequest.of(0, maxRows))) {
            row(csv, v.msisdn(), v.clientName(), v.sessions(), v.completed(), v.leftOrTimedOut(), v.notRegistered(),
                    v.other(), v.screens(), v.firstVisit(), v.lastVisit());
        }
        return csv.toString();
    }

    // ── mapping ────────────────────────────────────────────────────────────

    static String status(ESessionEndReason reason, boolean closed) {
        if (reason != null) return reason.name();
        return closed ? "ENDED" : IN_PROGRESS;
    }

    static String lastScreen(UssdSession s) {
        int level = s.getLevel() == null ? 0 : s.getLevel();
        if (level == 0) return "Start";
        if (level == UssdController.LEVEL_LANGUAGE) return "Language";
        if (level == UssdController.LEVEL_CONSENT) return "Terms & conditions";
        if (level == UssdController.LEVEL_AUTH) {
            return UssdController.STEP_PIN_SETUP.equals(s.getStep()) || UssdController.STEP_PIN_CONFIRM.equals(s.getStep())
                    ? "PIN setup" : "PIN entry";
        }
        if (level == CommonMenus.LEVEL_MAIN_MENU || s.getService() == null) return "Main menu";
        String service = SERVICE_LABELS.getOrDefault(s.getService(), s.getService());
        return s.getStep() == null ? service : service + " > " + humanize(s.getStep());
    }

    private static SessionView view(UssdSession s) {
        LocalDateTime end = s.getEndedAt() != null ? s.getEndedAt() : s.getUpdatedAt();
        long seconds = s.getCreatedAt() == null || end == null ? 0 : Math.max(0, Duration.between(s.getCreatedAt(), end).toSeconds());
        return new SessionView(s.getId(), s.getSessionId(), s.getMsisdn(), s.getClientName(), s.getNetworkCode(),
                s.getLanguage(), status(s.getEndReason(), s.isCloseCurrentSession()), lastScreen(s), s.isAuthenticated(),
                s.getRequestCount() == null ? 0 : s.getRequestCount(), seconds, s.getCreatedAt(), s.getEndedAt());
    }

    private static Specification<UssdSession> filter(LocalDateTime from, LocalDateTime to, String msisdn, String status) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(cb.between(root.get("createdAt"), from, to));
            if (msisdn != null && !msisdn.isBlank()) p.add(cb.like(root.get("msisdn"), likePattern(msisdn)));
            if (status != null && !status.isBlank()) {
                String st = status.trim().toUpperCase(Locale.ROOT);
                switch (st) {
                    case IN_PROGRESS -> {
                        p.add(cb.isNull(root.get("endReason")));
                        p.add(cb.isFalse(root.get("closeCurrentSession")));
                    }
                    case "ENDED" -> {
                        p.add(cb.isNull(root.get("endReason")));
                        p.add(cb.isTrue(root.get("closeCurrentSession")));
                    }
                    default -> p.add(cb.equal(root.get("endReason"), ESessionEndReason.valueOf(st)));
                }
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static String likePattern(String msisdn) {
        if (msisdn == null || msisdn.isBlank()) return "%";
        String digits = msisdn.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) digits = digits.substring(1);
        return "%" + digits + "%";
    }

    private static String humanize(String step) {
        String s = step.replace(':', ' ').replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString().substring(0, 10));
    }

    private static void row(StringBuilder csv, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) csv.append(',');
            String v = cells[i] == null ? "" : cells[i].toString();
            if (v.matches("^[=+\\-@].*")) v = "'" + v;
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) v = "\"" + v.replace("\"", "\"\"") + "\"";
            csv.append(v);
        }
        csv.append('\n');
    }
}
