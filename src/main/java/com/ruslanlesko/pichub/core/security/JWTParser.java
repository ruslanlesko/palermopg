package com.ruslanlesko.pichub.core.security;

import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.RsaKeyUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

public class JWTParser {
    private static final String KEY_PATH = "";

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

    public void parseToken(String token) {
    }

    public static void main(String[] args) {
        new JWTParser();
    }
}
