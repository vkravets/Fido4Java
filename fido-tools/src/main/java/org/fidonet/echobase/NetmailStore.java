package org.fidonet.echobase;

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Message;

import java.util.List;

/** Durable netmail delivery, separate from conference message areas. */
public interface NetmailStore {
    void saveNetmail(Message message, String deliveryKey, String nextHop);

    List<Message> getLocalNetmail(FTNAddr address);

    default List<Message> getEditorNetmail(FTNAddr address) {
        return getLocalNetmail(address);
    }

    List<QueuedNetmail> getQueuedNetmail();

    void markNetmailSent(String deliveryKey);
}
