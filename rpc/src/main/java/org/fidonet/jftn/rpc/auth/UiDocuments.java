package org.fidonet.jftn.rpc.auth;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;

/** Account-owned, bounded JSON data. Documents cannot modify node configuration or identities. */
final class UiDocuments {
    private static final Set<String> KEYS = Set.of("workspace", "mail-themes", "mail-templates");
    private static final JsonFactory JSON = JsonFactory.builder()
                                                       .streamReadConstraints(StreamReadConstraints.builder()
                                                                                                   .maxNestingDepth(16)
                                                                                                   .maxStringLength(60000)
                                                                                                   .maxNumberLength(20)
                                                                                                   .build())
                                                       .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                                       .build();
    private final RpcAccounts accounts;

    UiDocuments(RpcAccounts accounts) {
        this.accounts = accounts;
    }

    String read(String owner, String key) throws Exception {
        key(key);
        try (Connection connection = accounts.open();
                PreparedStatement sql = connection.prepareStatement("SELECT document FROM rpc_ui_documents WHERE owner=? AND document_key=?")) {
            sql.setString(1, owner);
            sql.setString(2, key);
            try (ResultSet rows = sql.executeQuery()) {
                return rows.next() ? rows.getString(1) : "{}";
            }
        }
    }

    void save(String owner, String key, String document) throws Exception {
        key(key);
        validate(document);
        try (Connection connection = accounts.open();
                PreparedStatement sql = connection.prepareStatement("MERGE INTO rpc_ui_documents(owner,document_key,document) KEY(owner,document_key) VALUES(?,?,?)")) {
            sql.setString(1, owner);
            sql.setString(2, key);
            sql.setString(3, document);
            sql.executeUpdate();
        }
    }

    private static void key(String key) {
        if (!KEYS.contains(key))
            throw new IllegalArgumentException("Unknown personal UI document");
    }

    private static void validate(String document) {
        if (document == null || document.getBytes(StandardCharsets.UTF_8).length > 262144)
            throw new IllegalArgumentException("Personal UI document exceeds 256 KiB");
        try (JsonParser parser = JSON.createParser(document)) {
            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new IllegalArgumentException("Personal UI document must be a JSON object");
            parser.skipChildren();
            if (parser.nextToken() != null)
                throw new IllegalArgumentException("Unexpected content after personal UI document");
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid personal UI JSON document", failure);
        }
    }
}
