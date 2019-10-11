package com.ruslanlesko.pichub.core.security;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import javax.enterprise.context.ApplicationScoped;
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


@ApplicationScoped
public class JWTParser {
    private static final String KEY_PATH = System.getenv("PIC_KEY");
    private static final String USER_ID_ATTR = "userId";

    private JwtConsumer consumer;

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
        byte[] encoded = Base64.getDecoder().decode(strKey);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String readPemKey() {
        try {
            return Files.readAllLines(Path.of(KEY_PATH)).stream()
                    .filter(s -> !s.contains("PUBLIC KEY"))
                    .collect(Collectors.joining());
        } catch (IOException e) {
            return null;
        }
    }

    public boolean validateTokenForUserId(String token, long userId) {
        try {
            JwtClaims claims = consumer.processToClaims(token);
            return claims.getClaimValue(USER_ID_ATTR, Long.class).equals(userId);
        } catch (InvalidJwtException | MalformedClaimException e) {
            System.out.println("JWT is invalid: " + e.getMessage());
            return false;
        }
    }
}
