package org.fidonet.echobase;

import org.fidonet.db.robots.EditableRobotRules;
import org.fidonet.storage.RepositoryAccess;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** Administrative and reader capabilities, independent of any database driver or ORM. */
public interface MailRepository
        extends
            MailStorage,
            NetmailRoutingStore,
            ArchiveStore,
            RepositoryAccess {
    Map<String, AccessLevel> getEchoAccess(Link link);

    List<StoredMail> readEchoPage(String area, long after, int limit) throws IOException;

    StoredMail findEchoMessageId(String area, String messageId) throws IOException;

    List<StoredMail> readEchoRecent(String area, long before, int limit) throws IOException;

    List<StoredMail> readEchoReplies(String area,
                                     String messageId,
                                     long after,
                                     int limit,
                                     long excluded)
            throws IOException;

    List<StoredMail> readNetmailPage(Collection<FTNAddr> local, boolean sent, long after, int limit)
            throws IOException;

    int waitingNetmail(Collection<FTNAddr> addresses) throws Exception;

    int releaseNetmail(Collection<FTNAddr> addresses) throws Exception;

    void cancelWaitingNetmail(long id, Collection<FTNAddr> addresses) throws Exception;

    void configureLinksAtomically(List<Link> links,
                                  List<String> packetPasswords,
                                  Callable<Void> publication)
            throws Exception;

    @Override
    EditableRobotRules getRobotRules();
}
