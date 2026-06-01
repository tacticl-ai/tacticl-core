package io.tacticl.client.discord;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.client.discord.exception.DiscordErrorDetails;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies the Ed25519 signature Discord attaches to every interaction webhook delivery.
 *
 * <p>Discord signs the concatenation of the {@code X-Signature-Timestamp} header and the raw
 * (unparsed) request body, and sends the signature in the {@code X-Signature-Ed25519} header
 * (hex). The application's public key (also hex, the raw 32-byte little-endian point) is the
 * verification key. Discord requires that a request failing verification be answered with HTTP
 * 401 — the controller maps {@link DiscordErrorDetails#INVALID_SIGNATURE} to 401.
 *
 * <p>Pure JDK 25: {@code Signature.getInstance("Ed25519")} and raw-point decoding via
 * {@link EdECPublicKeySpec}. No BouncyCastle.
 */
public class DiscordEd25519Verifier {

    private static final Logger logger = LoggerFactory.getLogger(DiscordEd25519Verifier.class);
    private static final String MODULE_NAME = "client-discord";

    private final DiscordConfig config;

    public DiscordEd25519Verifier(DiscordConfig config) {
        this.config = config;
    }

    /**
     * @param signatureHex value of the {@code X-Signature-Ed25519} header (hex)
     * @param timestamp    value of the {@code X-Signature-Timestamp} header
     * @param rawBody      the exact bytes of the request body as received (must not be re-serialized)
     * @return {@code true} if the signature is valid for this application's public key
     */
    public boolean verify(String signatureHex, String timestamp, byte[] rawBody) {
        if (signatureHex == null || timestamp == null || rawBody == null) {
            return false;
        }
        String publicKeyHex = config.getPublicKey();
        if (publicKeyHex == null || publicKeyHex.isBlank()) {
            throw new CidadelException(DiscordErrorDetails.INVALID_PUBLIC_KEY, MODULE_NAME,
                "discord public key not configured");
        }
        try {
            byte[] signature = HexFormat.of().parseHex(signatureHex);
            byte[] tsBytes = timestamp.getBytes(StandardCharsets.UTF_8);
            byte[] signed = new byte[tsBytes.length + rawBody.length];
            System.arraycopy(tsBytes, 0, signed, 0, tsBytes.length);
            System.arraycopy(rawBody, 0, signed, tsBytes.length, rawBody.length);

            PublicKey publicKey = decodePublicKey(publicKeyHex);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(signed);
            return verifier.verify(signature);
        }
        catch (IllegalArgumentException e) {
            // malformed hex in the signature header → treat as an invalid signature, not a 500
            logger.debug("Discord signature verification failed: malformed signature hex");
            return false;
        }
        catch (CidadelException e) {
            throw e;
        }
        catch (Exception e) {
            logger.warn("Discord signature verification errored", e);
            return false;
        }
    }

    /**
     * Decodes Discord's raw 32-byte little-endian Ed25519 public key (hex) into a {@link PublicKey}.
     * The high bit of the last byte encodes the sign of the x-coordinate; the remaining bits are the
     * y-coordinate in little-endian.
     */
    private PublicKey decodePublicKey(String publicKeyHex) throws Exception {
        byte[] keyBytes = HexFormat.of().parseHex(publicKeyHex);
        if (keyBytes.length != 32) {
            throw new CidadelException(DiscordErrorDetails.INVALID_PUBLIC_KEY, MODULE_NAME,
                "expected 32-byte Ed25519 public key, got " + keyBytes.length);
        }
        boolean xOdd = (keyBytes[31] & 0x80) != 0;
        byte[] yLe = keyBytes.clone();
        yLe[31] &= 0x7f;
        // little-endian → big-endian for BigInteger
        byte[] yBe = new byte[32];
        for (int i = 0; i < 32; i++) {
            yBe[i] = yLe[31 - i];
        }
        BigInteger y = new BigInteger(1, yBe);
        EdECPoint point = new EdECPoint(xOdd, y);
        NamedParameterSpec paramSpec = new NamedParameterSpec("Ed25519");
        EdECPublicKeySpec keySpec = new EdECPublicKeySpec(paramSpec, point);
        return KeyFactory.getInstance("Ed25519").generatePublic(keySpec);
    }
}
