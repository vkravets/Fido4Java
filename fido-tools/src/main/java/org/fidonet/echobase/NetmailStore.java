package org.fidonet.echobase;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.io.IOException;
import java.util.List;

/** Durable netmail delivery, separate from conference message areas. */
public interface NetmailStore {
    String UNPACKED = "nopack";

    static boolean unpacked(String nextHop) {
        return UNPACKED.equals(nextHop) || nextHop != null && nextHop.startsWith(UNPACKED + ":");
    }
    void saveNetmail(Message message, String deliveryKey, String nextHop);

    void stageNetmail(Message message, String deliveryKey, String nextHop);

    /** A different delivery of stored mail is a dupe; resuming its original delivery is not. */
    boolean isDuplicateNetmail(Message message, String deliveryKey);

    List<Message> getLocalNetmail(FTNAddr address);

    default List<Message> getEditorNetmail(FTNAddr address) {
        return getLocalNetmail(address);
    }

    List<QueuedNetmail> getQueuedNetmail();

    default void visitEditorNetmail(FTNAddr address, MessageIteration.Handler visitor)
            throws IOException {
        for (Message message : getEditorNetmail(address)) {
            visitor.accept(message);
        }
    }

    default void visitQueuedNetmail(QueuedHandler visitor) throws IOException {
        for (QueuedNetmail message : getQueuedNetmail()) {
            visitor.accept(message);
        }
    }

    interface QueuedHandler {
        void accept(QueuedNetmail message) throws IOException;
    }

    void markNetmailSent(String deliveryKey);

    /** Freezes the first real next hop when a nopack rule stops matching. */
    void routeUnpackedNetmail(String deliveryKey, String nextHop);
}
