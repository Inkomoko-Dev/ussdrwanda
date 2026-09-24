package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ELoanStatus;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.models.dto.RepaymentDto;
import com.inkomoko.mobilebanking.models.dto.fineract.FineractDtos;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Apache Fineract REST (v1) client. Uses one pooled JDK HttpClient (persistent keep-alive
 * connections) to keep per-menu latency low.
 *
 * Endpoints used:
 *   GET  /search?query={phone}&resource=clients&exactMatch=true
 *   GET  /clients/{id}
 *   GET  /clients/{id}/accounts?fields=loanAccounts
 *   GET  /loans/{id}?associations=repaymentSchedule
 *   GET  /loans/{id}?associations=transactions
 *   POST /loans/{id}/transactions?command=repayment
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.fineract.mode", havingValue = "live")
public class FineractRestService implements FineractService {

    private static final DateTimeFormatter FINERACT_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final RestClient client;
    private final InkomokoProperties.Fineract cfg;

    public FineractRestService(InkomokoProperties props) {
        this.cfg = props.getFineract();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(http);
        rf.setReadTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));
        this.client = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(rf)
                .defaultHeader("Fineract-Platform-TenantId", cfg.getTenantId())
                .defaultHeaders(h -> h.setBasicAuth(cfg.getUsername(), cfg.getPassword()))
                .build();
        log.info("Fineract live client configured: baseUrl={} tenant={}", cfg.getBaseUrl(), cfg.getTenantId());
    }

    @Override
    public Optional<ClientDto> findClientByMsisdn(String msisdn) {
        try {
            for (String variant : PhoneUtils.dialVariants(msisdn)) {
                List<FineractDtos.SearchResult> hits = client.get()
                        .uri(u -> u.path("/search")
                                .queryParam("query", variant)
                                .queryParam("resource", "clients")
                                .queryParam("exactMatch", true)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<FineractDtos.SearchResult>>() {});
                if (hits == null) continue;
                for (FineractDtos.SearchResult hit : hits) {
                    if (hit.entityId() == null || !"CLIENT".equalsIgnoreCase(hit.entityType())) continue;
                    FineractDtos.Client c = client.get().uri("/clients/{id}", hit.entityId())
                            .retrieve().body(FineractDtos.Client.class);
                    if (c != null && PhoneUtils.samePhone(c.mobileNo(), msisdn)) {
                        boolean active = Boolean.TRUE.equals(c.active())
                                || (c.status() != null && Integer.valueOf(300).equals(c.status().id()));
                        return Optional.of(new ClientDto(c.id(), c.displayName(), c.mobileNo(), active));
                    }
                }
            }
            return Optional.empty();
        } catch (RestClientException e) {
            throw new FineractException("Client lookup failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<LoanSummaryDto> getClientLoans(Long clientId) {
        try {
            FineractDtos.ClientAccounts accounts = client.get()
                    .uri(u -> u.path("/clients/{id}/accounts").queryParam("fields", "loanAccounts").build(clientId))
                    .retrieve().body(FineractDtos.ClientAccounts.class);
            if (accounts == null || accounts.loanAccounts() == null) return List.of();
            List<LoanSummaryDto> out = new ArrayList<>();
            for (FineractDtos.LoanAccount a : accounts.loanAccounts()) {
                ELoanStatus status = ELoanStatus.fromFineract(a.status() == null ? null : a.status().id(), a.inArrears());
                out.add(new LoanSummaryDto(a.id(), clientId, a.accountNo(), a.productName(), status,
                        null, a.loanBalance(), null, null, null));
            }
            return out;
        } catch (RestClientException e) {
            throw new FineractException("Loan list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public LoanSummaryDto getLoanDetails(Long loanId) {
        try {
            FineractDtos.Loan loan = client.get()
                    .uri(u -> u.path("/loans/{id}").queryParam("associations", "repaymentSchedule").build(loanId))
                    .retrieve().body(FineractDtos.Loan.class);
            if (loan == null) throw new FineractException("Loan " + loanId + " not found");

            LocalDate nextDue = null;
            BigDecimal nextInstallment = null;
            if (loan.repaymentSchedule() != null && loan.repaymentSchedule().periods() != null) {
                for (FineractDtos.Period p : loan.repaymentSchedule().periods()) {
                    if (p.period() == null || Boolean.TRUE.equals(p.complete())) continue;
                    BigDecimal out = p.totalOutstandingForPeriod();
                    if (out != null && out.signum() > 0) {
                        nextDue = DateUtility.fromFineract(p.dueDate());
                        nextInstallment = out;
                        break;
                    }
                }
            }
            FineractDtos.Summary s = loan.summary();
            return new LoanSummaryDto(loan.id(), loan.clientId(), loan.accountNo(), loan.loanProductName(),
                    ELoanStatus.fromFineract(loan.status() == null ? null : loan.status().id(), loan.inArrears()),
                    s == null ? null : s.principalOutstanding(),
                    s == null ? null : s.totalOutstanding(),
                    s == null ? null : s.totalOverdue(),
                    nextDue, nextInstallment);
        } catch (RestClientException e) {
            throw new FineractException("Loan details failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RepaymentDto> getRepayments(Long loanId, int limit) {
        List<RepaymentDto> list = new ArrayList<>();
        for (FineractDtos.Transaction t : repaymentTransactions(loanId)) {
            list.add(new RepaymentDto(t.id(), DateUtility.fromFineract(t.date()), t.amount(),
                    t.externalId() != null ? t.externalId() : String.valueOf(t.id())));
        }
        list.sort(Comparator.comparing(RepaymentDto::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RepaymentDto::transactionId, Comparator.nullsLast(Comparator.reverseOrder())));
        return limit > 0 && list.size() > limit ? list.subList(0, limit) : list;
    }

    @Override
    public Optional<String> findRepaymentByExternalId(Long loanId, String externalId) {
        return repaymentTransactions(loanId).stream()
                .filter(t -> externalId.equals(t.externalId()))
                .map(t -> String.valueOf(t.id()))
                .findFirst();
    }

    private List<FineractDtos.Transaction> repaymentTransactions(Long loanId) {
        try {
            FineractDtos.Loan loan = client.get()
                    .uri(u -> u.path("/loans/{id}").queryParam("associations", "transactions").build(loanId))
                    .retrieve().body(FineractDtos.Loan.class);
            if (loan == null || loan.transactions() == null) return List.of();
            return loan.transactions().stream()
                    .filter(t -> t.type() != null && Boolean.TRUE.equals(t.type().repayment()))
                    .filter(t -> !Boolean.TRUE.equals(t.manuallyReversed()))
                    .toList();
        } catch (RestClientException e) {
            throw new FineractException("Loan transactions failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String postRepayment(Long loanId, BigDecimal amount, LocalDate date, String externalId,
                                String receiptNumber, String note) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("locale", "en");
        body.put("transactionDate", date.format(FINERACT_DATE));
        body.put("transactionAmount", amount);
        body.put("paymentTypeId", cfg.getPaymentTypeId());
        body.put("externalId", externalId);
        body.put("receiptNumber", receiptNumber);
        body.put("note", note);
        try {
            FineractDtos.CommandResult r = client.post()
                    .uri(u -> u.path("/loans/{id}/transactions").queryParam("command", "repayment").build(loanId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(FineractDtos.CommandResult.class);
            if (r == null || r.resourceId() == null) {
                throw new FineractException("Repayment posted but no transaction id returned");
            }
            return String.valueOf(r.resourceId());
        } catch (RestClientException e) {
            throw new FineractException("Repayment posting failed: " + e.getMessage(), e);
        }
    }
}
