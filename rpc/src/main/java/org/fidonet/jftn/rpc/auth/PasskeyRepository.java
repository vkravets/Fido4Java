package org.fidonet.jftn.rpc.auth;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** RP-scoped database adapter; a credential can only resolve to its actual enabled owner. */
final class PasskeyRepository implements CredentialRepository {
    private final RpcAccounts accounts;
    private final String rp;

    PasskeyRepository(RpcAccounts accounts, String rp) {
        this.accounts = accounts;
        this.rp = rp;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        Set<PublicKeyCredentialDescriptor> result = new HashSet<>();
        accounts.credentials(rp, username, null)
                .forEach(credential -> result.add(PublicKeyCredentialDescriptor.builder()
                                                                               .id(credential.getCredentialId())
                                                                               .build()));
        return result;
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return accounts.handle(username);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray handle) {
        return accounts.username(handle);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray id, ByteArray handle) {
        return accounts.credentials(rp, null, id.getBase64Url())
                       .stream()
                       .filter(credential -> credential.getUserHandle().equals(handle))
                       .findFirst();
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray id) {
        return new HashSet<>(accounts.credentials(rp, null, id.getBase64Url()));
    }
}
