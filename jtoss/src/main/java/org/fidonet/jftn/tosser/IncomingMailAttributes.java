package org.fidonet.jftn.tosser;

import org.fidonet.types.Message;

/** Husky createXMSG receive-time normalization; delivery flavours belong to the receiving node. */
final class IncomingMailAttributes {
    private IncomingMailAttributes() {
    }

    static void apply(Message message, boolean local) {
        int attributes = message.getAttributes() & ~(0x0002 | 0x0008 | 0x0100 | 0x0200);
        if (message.isEchomail()) {
            attributes &= ~(0x0004 | 0x0080 | 0x0800);
        } else if (local) {
            attributes = (attributes & ~(0x0004 | 0x0020 | 0x0080 | 0x0800)) | 0x0001;
        } else {
            attributes |= 0x0020;
        }
        message.setAttributes(attributes);
    }
}
