package com.inkomoko.mobilebanking.security;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.admin.AdminUser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and verifies HS256-signed portal access tokens. */
@Slf4j
@Service
public class JwtService {

    public record TokenClaims(String username, Instant issuedAt) {
    }

    private static final String ISSUER = "inkomoko-ussd";

    private final byte[] secret;

    public JwtService(InkomokoProperties props) {
        String s = props.getSecurity().getJwtSecret();
        if (s == null || s.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("inkomoko.security.jwt-secret (JWT_SECRET) must be at least 32 characters");
        }
        this.secret = s.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(AdminUser user, int minutes) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Math.max(minutes, 5), ChronoUnit.MINUTES)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign token", e);
        }
    }

    public Optional<TokenClaims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) return Optional.empty();
            if (!jwt.verify(new MACVerifier(secret))) return Optional.empty();
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(c.getIssuer()) || c.getExpirationTime() == null
                    || c.getExpirationTime().before(new Date()) || c.getIssueTime() == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(c.getSubject(), c.getIssueTime().toInstant()));
        } catch (ParseException | JOSEException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
