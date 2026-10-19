package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.echobase.MailStorage;
import org.fidonet.jftn.tosser.EchoRouter;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import java.util.List;
import java.util.Locale;

/** Seeds configured links and file-managed echo subscriptions in the mail database. */
final class MailDatabaseConfiguration {
    private MailDatabaseConfiguration() {
    }

    static void apply(JFtnConfig config, MailStorage base) {
        boolean managed = EchoRouter.databaseSubscriptions(config);
        for (Link link : config.getConfiguredLinks()) {
            base.configureLink(link, config.getPacketPassword(link));
        }
        if (managed) {
            return;
        }
        List<String> areas = config.getValuesAsList("EchoArea");
        if (areas == null) {
            return;
        }
        for (String value : areas) {
            String[] fields = value.split(",");
            if (fields.length < 2 || fields[0].trim().isEmpty()) {
                throw new IllegalArgumentException("Expected EchoArea area,address[=READ|WRITE|BOTH],...");
            }
            for (int i = 1; i < fields.length; i++) {
                String[] recipient = fields[i].trim().split("=", 2);
                Link link = config.getLink(new FTNAddr(recipient[0].trim()));
                if (link == null) {
                    throw new IllegalArgumentException("Unknown echo link " + recipient[0]);
                }
                AccessLevel access = recipient.length == 1
                        ? AccessLevel.BOTH
                        : AccessLevel.valueOf(recipient[1].trim().toUpperCase(Locale.ROOT));
                base.configureSubscription(link, fields[0].trim(), access);
            }
        }
    }

}
