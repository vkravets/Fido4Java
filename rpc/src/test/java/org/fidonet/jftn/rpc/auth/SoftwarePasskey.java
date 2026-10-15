package org.fidonet.jftn.rpc.auth;

import org.fidonet.jftn.rpc.PasskeyOptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** A test-only authenticator that signs actual WebAuthn data with a generated EC private key. */
final class SoftwarePasskey {
    private static final ObjectMapper json = new ObjectMapper();
    private final KeyPair key;
    private final byte[] id = RpcAccounts.randomBytes(32);
    private final String handle;
    private final String rp;

    SoftwarePasskey(PasskeyOptions options) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        key = generator.generateKeyPair();
        JsonNode request = options(options);
        handle = request.get("user").get("id").asText();
        rp = request.get("rp").get("id").asText();
    }

    private JsonNode options(PasskeyOptions options) throws Exception {
        return json.readTree(options.getOptionsJson()).get("publicKey");
    }

    String registration(PasskeyOptions options, String origin, boolean uv) throws Exception {
        ECPublicKey publicKey = (ECPublicKey) key.getPublic();
        Map<Object, Object> cose = new LinkedHashMap<>();
        cose.put(1, 2);
        cose.put(3, -7);
        cose.put(-1, 1);
        cose.put(-2, coordinate(publicKey.getW().getAffineX().toByteArray()));
        cose.put(-3, coordinate(publicKey.getW().getAffineY().toByteArray()));
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(authData(uv ? 0x45 : 0x41, 0));
        data.write(new byte[16]);
        data.write(new byte[]{0, (byte) id.length});
        data.write(id);
        data.write(cbor(cose));
        Map<String, Object> attestation = new LinkedHashMap<>();
        attestation.put("fmt", "none");
        attestation.put("attStmt", Map.of());
        attestation.put("authData", data.toByteArray());
        return credential(Map.of("clientDataJSON",
                                 encode(clientData(options, origin, "webauthn.create")),
                                 "attestationObject",
                                 encode(cbor(attestation)),
                                 "transports",
                                 new String[]{"internal"}));
    }

    String assertion(PasskeyOptions options, String origin, int counter, boolean uv)
            throws Exception {
        byte[] client = clientData(options, origin, "webauthn.get");
        byte[] data = authData(uv ? 5 : 1, counter);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key.getPrivate());
        signature.update(data);
        signature.update(MessageDigest.getInstance("SHA-256").digest(client));
        return credential(Map.of("clientDataJSON",
                                 encode(client),
                                 "authenticatorData",
                                 encode(data),
                                 "signature",
                                 encode(signature.sign()),
                                 "userHandle",
                                 handle));
    }

    private byte[] clientData(PasskeyOptions options, String origin, String type) throws Exception {
        return json.writeValueAsBytes(Map.of("type",
                                             type,
                                             "origin",
                                             origin,
                                             "challenge",
                                             options(options).get("challenge").asText(),
                                             "crossOrigin",
                                             false));
    }

    private byte[] authData(int flags, int counter) throws Exception {
        return ByteBuffer.allocate(37)
                         .put(MessageDigest.getInstance("SHA-256")
                                           .digest(rp.getBytes(StandardCharsets.UTF_8)))
                         .put((byte) flags)
                         .putInt(counter)
                         .array();
    }

    private String credential(Map<String, Object> response) throws Exception {
        return json.writeValueAsString(Map.of("id",
                                              encode(id),
                                              "rawId",
                                              encode(id),
                                              "type",
                                              "public-key",
                                              "response",
                                              response,
                                              "clientExtensionResults",
                                              Map.of()));
    }

    String id() {
        return encode(id);
    }

    String corruptSignature(String response) throws Exception {
        var value = json.readTree(response);
        byte[] signature = Base64.getUrlDecoder()
                                 .decode(value.get("response").get("signature").asText());
        signature[signature.length - 1] ^= 1;
        ((ObjectNode) value.get("response")).put("signature", encode(signature));
        return json.writeValueAsString(value);
    }

    String withUserHandle(String response, String handle) throws Exception {
        var value = json.readTree(response);
        ((ObjectNode) value.get("response")).put("userHandle", handle);
        return json.writeValueAsString(value);
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] coordinate(byte[] bytes) {
        byte[] result = new byte[32];
        System.arraycopy(bytes,
                         Math.max(0, bytes.length - 32),
                         result,
                         Math.max(0, 32 - bytes.length),
                         Math.min(32, bytes.length));
        return result;
    }

    /** Minimal CBOR encoding of the maps/integers/byte strings required by a test attestation. */
    private byte[] cbor(Object value) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            prefix(out, 5, map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.write(cbor(entry.getKey()));
                out.write(cbor(entry.getValue()));
            }
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            prefix(out, 2, bytes.length);
            out.write(bytes);
        } else if (value instanceof String) {
            byte[] bytes = ((String) value).getBytes(StandardCharsets.UTF_8);
            prefix(out, 3, bytes.length);
            out.write(bytes);
        } else {
            int integer = ((Number) value).intValue();
            prefix(out, integer >= 0 ? 0 : 1, integer >= 0 ? integer : -1 - integer);
        }
        return out.toByteArray();
    }

    private void prefix(ByteArrayOutputStream out, int type, int value) {
        if (value < 24) {
            out.write((type << 5) | value);
        } else if (value < 256) {
            out.write((type << 5) | 24);
            out.write(value);
        } else {
            out.write((type << 5) | 25);
            out.write(value >> 8);
            out.write(value & 255);
        }
    }
}
