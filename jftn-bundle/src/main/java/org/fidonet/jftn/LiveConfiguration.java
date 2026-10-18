package org.fidonet.jftn;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.rpc.auth.ProfileHandler;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.jftn.web.NodeConfigurationService;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates/stages changes before publishing SQL, file and runtime snapshots. */
public final class LiveConfiguration implements NodeConfigurationService.Application {
    private static final Logger logger = LoggerFactory.getLogger(LiveConfiguration.class);
    private static final Set<String> LIVE = Set.of("link",
                                                   "packetpassword",
                                                   "system",
                                                   "sysop",
                                                   "location",
                                                   "ndl",
                                                   "nodelisturl",
                                                   "nodelistcharset",
                                                   "preservereceivedfiles",
                                                   "deletetossed",
                                                   "enforceechoaccess",
                                                   "autocreateecho",
                                                   "autocreateechofrom",
                                                   "tossintervalseconds");
    private final JFtnConfig active;
    private final MailDatabaseFacade database;
    private final MailRuntime mail;
    private final PollService polls;
    private final SessionContext context;
    private final ProfileHandler profiles;

    public LiveConfiguration(JFtnConfig active,
                             MailDatabaseFacade database,
                             MailRuntime mail,
                             PollService polls,
                             SessionContext context,
                             ProfileHandler profiles) {
        this.active = active;
        this.database = database;
        this.mail = mail;
        this.polls = polls;
        this.context = context;
        this.profiles = profiles;
    }

    @Override
    public void apply(JFtnConfig candidate, NodeConfigurationService.Publication publication)
            throws Exception {
        mail.operate(tosser -> {
            applyWithPollQueuePaused(tosser, candidate, publication);
            logger.info("Configuration reloaded successfully; existing connections retain their session snapshots");
            return null;
        });
    }

    private void applyWithPollQueuePaused(Tosser tosser,
                                          JFtnConfig candidate,
                                          NodeConfigurationService.Publication publication)
            throws Exception {
        synchronized (polls) {
            applyWithSessionTemplatePaused(tosser, candidate, publication);
        }
    }

    private void applyWithSessionTemplatePaused(Tosser tosser,
                                                JFtnConfig candidate,
                                                NodeConfigurationService.Publication publication)
            throws Exception {
        synchronized (context) {
            publish(tosser, candidate, publication);
        }
    }

    private void publish(Tosser tosser,
                         JFtnConfig candidate,
                         NodeConfigurationService.Publication publication)
            throws Exception {
        validate(candidate);
        Runnable routers = tosser.prepareConfiguration(candidate);
        SessionContext template = JFtnRunner.createSessionContext(candidate);
        template.getStationConfig()
                .setPartialDirectory(context.getStationConfig().getPartialDirectory());
        List<String> addresses = new ArrayList<>();
        candidate.getLocalAddresses().forEach(address -> addresses.add(address.toString()));
        List<Link> links = candidate.getConfiguredLinks();
        List<String> passwords = new ArrayList<>();
        links.forEach(link -> passwords.add(candidate.getPacketPassword(link)));
        List<Runnable> abort = new ArrayList<>();
        long seconds = intervalSeconds(candidate);
        Runnable interval = seconds == intervalSeconds(active) ? () -> {
        } : mail.prepareInterval(seconds, abort);
        try {
            database.configureLinksAtomically(links, passwords, () -> {
                publication.run();
                return null;
            });
        } catch (Exception failure) {
            abort.forEach(Runnable::run);
            throw failure;
        }
        active.activate(candidate);
        routers.run();
        context.reconfigure(template);
        profiles.stationAddresses(addresses);
        interval.run();
    }

    private void validate(JFtnConfig candidate) {
        Map<String, List<String>> before = active.options();
        Map<String, List<String>> after = candidate.options();
        Set<String> keys = new HashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            if (!Objects.equals(before.get(key), after.get(key)) && !LIVE.contains(key)
                    && !(key.equals("rpcpassword") && !after.containsKey(key))
                    && !sameEffectiveMetadata(candidate, key)) {
                throw new IllegalArgumentException("Configuration option requires a controlled restart: "
                        + key);
            }
        }
        for (Link link : active.getConfiguredLinks()) {
            if (candidate.getLink(link.getAddr()) == null) {
                throw new IllegalArgumentException("Removing a link requires reviewing its queues and subscriptions first");
            }
        }
        Set<String> peers = new HashSet<>();
        for (String entry : candidate.getValuesAsList("Link")) {
            Link link = new Link(entry);
            if (!peers.add(link.getAddr().toString())) {
                throw new IllegalArgumentException("Duplicate configured link: " + link.getAddr());
            }
            if (link.getPort() < 0 || link.getPort() > 65535) {
                throw new IllegalArgumentException("Link port must be in 0..65535: "
                                                   + link.getAddr());
            }
            if (link.getMyaddr() == null || link.getMyaddr().isEmpty()) {
                throw new IllegalArgumentException("Link requires at least one local AKA: "
                                                   + link.getAddr());
            }
        }
        for (String flag : List.of("PreserveReceivedFiles",
                                   "EnforceEchoAccess",
                                   "AutoCreateEcho")) {
            String value = candidate.getValue(flag,
                                              flag.equals("AutoCreateEcho") ? "false" : "true");
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException(flag + " must be true or false");
            }
        }
        if (!List.of("0", "1").contains(candidate.getValue("Deletetossed", "1"))) {
            throw new IllegalArgumentException("Deletetossed must be 0 or 1");
        }
    }

    private boolean sameEffectiveMetadata(JFtnConfig candidate, String key) {
        String fallback;
        if (key.equals("rpchttporigin"))
            fallback = "http://localhost:9091";
        else if (key.equals("uimailstorage"))
            fallback = Path.of(active.getSourcePath().getParent().toString(), "mail").toString();
        else
            return false;
        return active.getValue(key, fallback).equals(candidate.getValue(key, fallback));
    }

    private long intervalSeconds(JFtnConfig config) {
        try {
            long value = Long.parseLong(config.getValue("TossIntervalSeconds", "5"));
            if (value >= 1 && value <= 86400)
                return value;
        } catch (NumberFormatException ignored) {
            // Report the option rather than echoing arbitrary configuration text.
        }
        throw new IllegalArgumentException("TossIntervalSeconds must be in 1..86400");
    }
}
