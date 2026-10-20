package org.fidonet.jftn.ui;

import org.fidonet.jftn.rpc.MailDraft;
import org.fidonet.jftn.rpc.storage.UserMailRepository;
import org.fidonet.storage.StorageServices;
import org.fidonet.storage.StorageSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/** Reader state facade; its storage lease is closed with the RPC service. */
final class UserMailStateStore implements AutoCloseable {
    private final StorageSession storage;
    private final UserMailRepository repository;

    UserMailStateStore(Path path) throws IOException {
        storage = StorageServices.file(path);
        try {
            repository = storage.require(UserMailRepository.class);
        } catch (IOException | RuntimeException failure) {
            try {
                storage.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    boolean isRead(String owner, String box, String area, long id) throws Exception {
        return repository.isRead(owner, box, area, id);
    }

    Set<Long> readIds(String owner, String box, String area, List<Long> ids) throws Exception {
        return repository.readIds(owner, box, area, ids);
    }

    Set<Long> deletedIds(String owner, String box, List<Long> ids) throws Exception {
        return repository.deletedIds(owner, box, ids);
    }

    void deleteMessage(String owner, String box, long id) throws Exception {
        repository.deleteMessage(owner, box, id);
    }

    void mark(String owner, String box, String area, long id, boolean read) throws Exception {
        repository.mark(owner, box, area, id, read);
    }

    List<MailDraft> drafts(String owner) throws Exception {
        return repository.drafts(owner);
    }

    void save(String owner, MailDraft draft) throws Exception {
        repository.save(owner, draft);
    }

    void delete(String owner, String id) throws Exception {
        repository.delete(owner, id);
    }

    String reserve(String id, String fingerprint, Callable<String> allocateId) throws Exception {
        return repository.reserve(id, fingerprint, allocateId);
    }

    @Override
    public void close() throws IOException {
        storage.close();
    }
}
