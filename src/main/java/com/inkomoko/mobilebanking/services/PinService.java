package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.EClientState;
import com.inkomoko.mobilebanking.enums.EPinStatus;
import com.inkomoko.mobilebanking.models.AccountAttempt;
import com.inkomoko.mobilebanking.models.ClientPin;
import com.inkomoko.mobilebanking.repositories.AccountAttemptRepository;
import com.inkomoko.mobilebanking.repositories.ClientPinRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * USSD PIN lifecycle: first-time setup, verification with attempt counting, lockout,
 * and support-driven reset/unlock. PINs are only ever held as bcrypt hashes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PinService {

    public enum VerifyResult { SUCCESS, WRONG, LOCKED }

    private final ClientPinRepository pinRepo;
    private final AccountAttemptRepository attemptRepo;
    private final PasswordEncoder encoder;
    private final InkomokoProperties props;
    private final AuditService audit;

    /** Decides whether the caller must set up a PIN, enter it, or is locked. */
    @Transactional
    public EClientState resolveState(String msisdn, Long clientId, String sessionId) {
        Optional<ClientPin> opt = pinRepo.findByMsisdn(msisdn);
        if (opt.isEmpty()) return EClientState.PIN_NOT_SET;
        ClientPin pin = opt.get();
        // Number now belongs to a different Fineract client (recycled SIM): old PIN must not work.
        if (pin.getClientId() != null && clientId != null && !pin.getClientId().equals(clientId)) {
            pin.setPinHash(null);
            pin.setStatus(EPinStatus.NOT_SET);
            pin.setClientId(clientId);
            pin.setUpdatedAt(DateUtility.nowKigali());
            pinRepo.save(pin);
            audit.ussd(msisdn, sessionId, "PIN_INVALIDATED_CLIENT_CHANGED", String.valueOf(clientId), true, null);
            return EClientState.PIN_NOT_SET;
        }
        return switch (pin.getStatus()) {
            case LOCKED -> EClientState.LOCKED;
            case ACTIVE -> pin.getPinHash() == null ? EClientState.PIN_NOT_SET : EClientState.ACTIVE;
            case NOT_SET -> EClientState.PIN_NOT_SET;
        };
    }

    public boolean isValidFormat(String pin) {
        int len = props.getUssd().getPinLength();
        return pin != null && pin.matches("[0-9]{" + len + "}");
    }

    /** Rejects trivially guessable PINs (all same digit, ascending/descending runs). */
    public boolean isWeak(String pin) {
        if (pin == null || pin.length() < 2) return true;
        boolean same = true, asc = true, desc = true;
        for (int i = 1; i < pin.length(); i++) {
            int d = pin.charAt(i) - pin.charAt(i - 1);
            if (d != 0) same = false;
            if (d != 1) asc = false;
            if (d != -1) desc = false;
        }
        return same || asc || desc;
    }

    public String hash(String rawPin) {
        return encoder.encode(rawPin);
    }

    public boolean matches(String rawPin, String hash) {
        return rawPin != null && hash != null && encoder.matches(rawPin, hash);
    }

    @Transactional
    public void setPin(String msisdn, Long clientId, String rawPin) {
        LocalDateTime now = DateUtility.nowKigali();
        ClientPin pin = pinRepo.findByMsisdn(msisdn).orElseGet(() -> {
            ClientPin p = new ClientPin();
            p.setMsisdn(msisdn);
            p.setCreatedAt(now);
            return p;
        });
        pin.setClientId(clientId);
        pin.setPinHash(encoder.encode(rawPin));
        pin.setStatus(EPinStatus.ACTIVE);
        pin.setLockedAt(null);
        pin.setUpdatedAt(now);
        pin.setLastLoginAt(now);
        pinRepo.save(pin);
        attemptRepo.findByMsisdn(msisdn).ifPresent(attemptRepo::delete);
    }

    /** Verifies the PIN; on the configured number of consecutive failures the PIN is locked. */
    @Transactional
    public VerifyResult verify(String msisdn, String rawPin) {
        ClientPin pin = pinRepo.findByMsisdn(msisdn).orElse(null);
        if (pin == null || pin.getStatus() == EPinStatus.LOCKED) return VerifyResult.LOCKED;

        if (isValidFormat(rawPin) && matches(rawPin, pin.getPinHash())) {
            attemptRepo.findByMsisdn(msisdn).ifPresent(attemptRepo::delete);
            pin.setLastLoginAt(DateUtility.nowKigali());
            pinRepo.save(pin);
            return VerifyResult.SUCCESS;
        }

        LocalDateTime now = DateUtility.nowKigali();
        AccountAttempt attempt = attemptRepo.findByMsisdn(msisdn)
                .orElseGet(() -> new AccountAttempt(msisdn, 0, now, now));
        attempt.setAttempts(attempt.getAttempts() + 1);
        attempt.setUpdatedAt(now);

        if (attempt.getAttempts() >= props.getUssd().getMaxPinAttempts()) {
            pin.setStatus(EPinStatus.LOCKED);
            pin.setLockedAt(now);
            pin.setUpdatedAt(now);
            pinRepo.save(pin);
            if (attempt.getId() != null) attemptRepo.delete(attempt);
            log.info("PIN locked for msisdn={}", msisdn);
            return VerifyResult.LOCKED;
        }
        attemptRepo.save(attempt);
        return VerifyResult.WRONG;
    }

    public int remainingAttempts(String msisdn) {
        int used = attemptRepo.findByMsisdn(msisdn).map(AccountAttempt::getAttempts).orElse(0);
        return Math.max(0, props.getUssd().getMaxPinAttempts() - used);
    }

    /** Support reset after identity verification: client sets a new PIN on next dial. */
    @Transactional
    public boolean reset(String msisdn) {
        Optional<ClientPin> opt = pinRepo.findByMsisdn(msisdn);
        if (opt.isEmpty()) return false;
        ClientPin pin = opt.get();
        pin.setPinHash(null);
        pin.setStatus(EPinStatus.NOT_SET);
        pin.setLockedAt(null);
        pin.setUpdatedAt(DateUtility.nowKigali());
        pinRepo.save(pin);
        attemptRepo.findByMsisdn(msisdn).ifPresent(attemptRepo::delete);
        return true;
    }

    /** Unlocks without changing the PIN. */
    @Transactional
    public boolean unlock(String msisdn) {
        Optional<ClientPin> opt = pinRepo.findByMsisdn(msisdn);
        if (opt.isEmpty()) return false;
        ClientPin pin = opt.get();
        pin.setStatus(pin.getPinHash() == null ? EPinStatus.NOT_SET : EPinStatus.ACTIVE);
        pin.setLockedAt(null);
        pin.setUpdatedAt(DateUtility.nowKigali());
        pinRepo.save(pin);
        attemptRepo.findByMsisdn(msisdn).ifPresent(attemptRepo::delete);
        return true;
    }

    public Optional<ClientPin> find(String msisdn) {
        return pinRepo.findByMsisdn(msisdn);
    }
}
