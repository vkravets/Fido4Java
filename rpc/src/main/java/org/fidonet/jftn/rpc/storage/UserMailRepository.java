package org.fidonet.jftn.rpc.storage;

import org.fidonet.jftn.rpc.MailDraft;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/** Owner-scoped reader state, drafts and idempotent submission reservations. */
public interface UserMailRepository extends AutoCloseable {
    boolean isRead(String owner, String box, String area, long id) throws Exception;

    Set<Long> readIds(String owner, String box, String area, List<Long> ids) throws Exception;

    Set<Long> deletedIds(String owner, String box, List<Long> ids) throws Exception;

    void deleteMessage(String owner, String box, long id) throws Exception;

    void mark(String owner, String box, String area, long id, boolean read) throws Exception;

    List<MailDraft> drafts(String owner) throws Exception;

    void save(String owner, MailDraft draft) throws Exception;

    void delete(String owner, String id) throws Exception;

    String reserve(String id, String fingerprint, Callable<String> allocateId) throws Exception;
}
