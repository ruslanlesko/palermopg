package com.leskor.palermopg.security;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

public class JWTParser {
    private static final Logger logger = LoggerFactory.getLogger("JWTParser");

    private static final String KEY_PATH = System.getenv("PIC_KEY");
    private static final String SECURITY_DISABLED = System.getenv("SECURITY_DISABLED");
    private static final String USER_ID_ATTR = "userId";
    private static final String BEARER = "Bearer ";
    private static final Long ADMIN_ID = Long.parseLong(System.getenv("PIC_ADMIN_ID") == null ? "-1" : System.getenv("PIC_ADMIN_ID"));

    private final JwtConsumer consumer;

    public JWTParser() {
        Key publicKey = makePublicKey();

        consumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setRequireSubject()
                .setVerificationKey(publicKey)
                .build();
    }

    private Key makePublicKey() {
        String strKey = readPemKey();
        if (strKey == null) {
            logger.error("Public key is null");
            return null;
        }
        byte[] encoded = Base64.getDecoder().decode(strKey);
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Cannot create public key: " + e.getMessage());
            return null;
        }
    }

    private String readPemKey() {
        try {
            return Files.readAllLines(Path.of(KEY_PATH)).stream()
                    .filter(s -> !s.contains("PUBLIC KEY"))
                    .collect(Collectors.joining());
        } catch (IOException e) {
            logger.error("Cannot read public key: " + e.getMessage());
            return null;
        }
    }

    public boolean validateTokenForUserId(String token, long userId) {
        if (SECURITY_DISABLED != null && SECURITY_DISABLED.equals("true")) {
            return true;
        }

        if (token == null) {
            return false;
        }

        if (token.startsWith(BEARER)) {
            token = token.substring(BEARER.length());
        }

        try {
            JwtClaims claims = consumer.processToClaims(token);
            return claims.getClaimValue(USER_ID_ATTR, Long.class).equals(userId);
        } catch (InvalidJwtException | MalformedClaimException e) {
            logger.warn("JWT is invalid: " + e.getMessage());
            return false;
        }
    }

    public boolean isAdmin(String token) {
        return validateTokenForUserId(token, ADMIN_ID);
    }
}
