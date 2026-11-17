package com.inkomoko.mobilebanking.controllers;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.dto.ComvivaUssdRequest;
import com.inkomoko.mobilebanking.models.dto.UssdRequest;
import com.inkomoko.mobilebanking.models.dto.UssdResponse;
import com.inkomoko.mobilebanking.services.CountryService;
import com.inkomoko.mobilebanking.services.UssdFlowService;
import com.inkomoko.mobilebanking.services.UssdFlowService.Inbound;
import com.inkomoko.mobilebanking.services.UssdFlowService.Result;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import com.inkomoko.mobilebanking.utils.UssdInputParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * USSD entry points for Inkomoko.
 *
 * <ul>
 *   <li>{@code POST /api/ussd} — Comviva gateway used by MTN and Airtel (plain text + {@code Freeflow})</li>
 *   <li>{@code POST /api/ussd/fdi} — FDI/Efashe gateway (JSON in / JSON out)</li>
 * </ul>
 *
 * Service code is configurable ({@code 662*105} pilot, {@code 311} once the Inkomoko shortcode is live).
 * Comviva's first-dial {@code input} is the shortcode itself (e.g. {@code 311}).
 */
@RestController
@RequestMapping("/api/ussd")
@Tag(name = "USSD", description = "Inkomoko USSD endpoints (Comviva + FDI)")
@RequiredArgsConstructor
@Slf4j
public class UssdController {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final Pattern MSISDN_PATTERN = Pattern.compile("^\\+?[0-9]{9,15}$");
    private static final Pattern INPUT_PATTERN = Pattern.compile("^[0-9*#]{0,182}$");
    private static final int MAX_INPUT_LENGTH = 200;

    /** @deprecated Prefer {@link UssdFlowService#LEVEL_LANGUAGE}; kept for older call sites. */
    public static final int LEVEL_LANGUAGE = UssdFlowService.LEVEL_LANGUAGE;
    public static final int LEVEL_CONSENT = UssdFlowService.LEVEL_CONSENT;
    public static final int LEVEL_AUTH = UssdFlowService.LEVEL_AUTH;
    public static final String STEP_PIN_SETUP = UssdFlowService.STEP_PIN_SETUP;
    public static final String STEP_PIN_CONFIRM = UssdFlowService.STEP_PIN_CONFIRM;

    private final UssdFlowService flow;
    private final CountryService countries;
    private final InkomokoProperties props;

    // ══════════════════════════════════════════════════════════════════════
    //  Comviva (MTN / Airtel) — POST /api/ussd
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Comviva USSD (MTN/Airtel)",
            description = "Plain-text body with Freeflow FC/FB. Body: sessionId, input, newRequest (1|0), msisdn.")
    public ResponseEntity<String> comviva(@RequestBody ComvivaUssdRequest req) {
        long started = System.nanoTime();
        Integer newRequest = req.newRequestFlag();
        String error = validateComviva(req, newRequest);
        if (error != null) {
            log.warn("USSD Comviva bad request: sessionId={} reason={}", req.getSessionId(), error);
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Invalid request: " + error);
        }

        String msisdn = PhoneUtils.purify(req.getMsisdn());
        String input = req.getInput() == null ? "" : req.getInput().trim();
        Result result;
        try {
            result = flow.handle(new Inbound(req.getSessionId(), msisdn, input,
                    PhoneUtils.networkCodeFromMsisdn(msisdn), countries.serviceCode(),
                    null, null, newRequest));
        } catch (IllegalArgumentException e) {
            log.warn("USSD Comviva rejected: sessionId={} reason={}", req.getSessionId(), e.getMessage());
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid request: " + e.getMessage());
        }

        long ms = (System.nanoTime() - started) / 1_000_000;
        if (result.text().length() > props.getUssd().getMaxResponseLength()) {
            log.warn("USSD screen longer than {} chars ({}): sessionId={}", props.getUssd().getMaxResponseLength(),
                    result.text().length(), req.getSessionId());
        }
        String freeflow = result.continueSession() ? "FC" : "FB";
        log.info("USSD Comviva | msisdn={} sessionId={} newRequest={} inputLen={} freeflow={} ms={}",
                msisdn, req.getSessionId(), newRequest, input.length(), freeflow, ms);

        return freeflowResponse(result.text(), freeflow);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FDI / Efashe — POST /api/ussd/fdi
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping(path = "/fdi", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "FDI USSD gateway",
            description = "JSON contract: sessionId, gwRef, gwTstamp, msisdn, networkCode, serviceCode, userInput → text, continueSession")
    public ResponseEntity<UssdResponse> fdi(@RequestBody UssdRequest req) {
        long started = System.nanoTime();

        String error = validateFdi(req);
        if (error != null) {
            log.warn("USSD FDI bad request: sessionId={} reason={}", req.getSessionId(), error);
            return ResponseEntity.badRequest().body(fdiResponse(req, null, "Invalid request: " + error, false));
        }
        String msisdn = PhoneUtils.purify(req.getMsisdn());
        String rawInput = req.getUserInput() == null ? "" : req.getUserInput().trim();

        Result result = flow.handle(new Inbound(req.getSessionId(), msisdn, rawInput,
                req.getNetworkCode(), req.getServiceCode(), req.getGwRef(), req.getGwTstamp(), null));

        long ms = (System.nanoTime() - started) / 1_000_000;
        if (result.text().length() > props.getUssd().getMaxResponseLength()) {
            log.warn("USSD screen longer than {} chars ({}): sessionId={}", props.getUssd().getMaxResponseLength(),
                    result.text().length(), req.getSessionId());
        }
        log.info("USSD FDI | msisdn={} sessionId={} inputLen={} continue={} ms={}", msisdn, req.getSessionId(),
                rawInput.length(), result.continueSession() ? 1 : 0, ms);

        return ResponseEntity.ok(fdiResponse(req, result.session() != null ? result.session().getAppRef() : null,
                result.text(), result.continueSession()));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Validation / response helpers
    // ══════════════════════════════════════════════════════════════════════

    private String validateComviva(ComvivaUssdRequest req, Integer newRequest) {
        if (newRequest == null) return "newRequest must be 1 (new session) or 0 (ongoing session)";
        if (req.getSessionId() == null || !SESSION_ID_PATTERN.matcher(req.getSessionId()).matches()) {
            return "sessionId missing or invalid";
        }
        if (req.getMsisdn() == null || !MSISDN_PATTERN.matcher(req.getMsisdn().trim()).matches()) {
            return "msisdn missing or invalid";
        }
        String input = req.getInput() == null ? "" : req.getInput().trim();
        if (newRequest == 1) {
            if (!UssdInputParser.startsWithServiceCode(input, countries.serviceCode())) {
                return "for newRequest=1, input must start with shortcode " + countries.serviceCode();
            }
        }
        if (!input.isEmpty() && !INPUT_PATTERN.matcher(input).matches()) {
            return "input contains unsupported characters";
        }
        if (input.length() > MAX_INPUT_LENGTH) return "input too long";
        return null;
    }

    private String validateFdi(UssdRequest req) {
        if (req.getSessionId() == null || !SESSION_ID_PATTERN.matcher(req.getSessionId()).matches()) {
            return "sessionId missing or invalid";
        }
        if (req.getMsisdn() == null || !MSISDN_PATTERN.matcher(req.getMsisdn().trim()).matches()) {
            return "msisdn missing or invalid";
        }
        if (req.getUserInput() != null && req.getUserInput().length() > MAX_INPUT_LENGTH) {
            return "userInput too long";
        }
        String expected = UssdInputParser.normalizeServiceCode(countries.serviceCode());
        String given = UssdInputParser.normalizeServiceCode(req.getServiceCode());
        if (!expected.isEmpty() && !given.isEmpty() && !given.equals(expected)) {
            return "unexpected serviceCode " + given;
        }
        return null;
    }

    private static ResponseEntity<String> freeflowResponse(String body, String freeflow) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Freeflow", freeflow);
        headers.add("Cache-Control", "max-age=0");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "-1");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .contentLength(bytes.length)
                .body(body);
    }

    private static UssdResponse fdiResponse(UssdRequest req, String appRef, String text, boolean cont) {
        return new UssdResponse(req.getSessionId(), req.getGwRef(), appRef,
                DateUtility.gatewayTimestamp(), text, cont ? 1 : 0);
    }
}
