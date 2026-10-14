package org.fidonet.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.fidonet.db.objects.ConfigurationLink;
import org.fidonet.db.objects.Echoarea;
import org.fidonet.db.objects.Echomail;
import org.fidonet.db.objects.Subscription;
import org.fidonet.echobase.AccessLevel;
import org.fidonet.types.Link;
import org.fidonet.types.Message;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class SubscriptionRegressionTest {
    private OrmManager database;
    private DatabaseManager manager;
    private Link link;

    @Before
    public void open() throws Exception {
        database = new OrmManager("jdbc:h2:mem:subscriptions" + System.nanoTime());
        database.connect();
        manager = new DatabaseManager(database);
        ConfigurationLink configuration = new ConfigurationLink();
        configuration.setAddress("2:467/1.0@fidonet.org");
        configuration.setPassword("");
        configuration.setPacket_password("");
        database.getDao(ConfigurationLink.class).create(configuration);
        link = configuration.toLink();
        Echoarea a = area("a"), b = area("b"), other = area("other");
        subscribe(configuration, a, 100L);
        subscribe(configuration, b, 10L);
        message(a, 50L, "old-a");
        message(a, 101L, "new-a");
        message(b, 9L, "old-b");
        message(b, 11L, "new-b");
        message(other, 200L, "unsubscribed");
    }

    @After
    public void close() {
        database.disconnect();
    }

    private Echoarea area(String name) throws Exception {
        Echoarea area = new Echoarea();
        area.setName(name);
        database.getDao(Echoarea.class).create(area);
        return area;
    }

    private void subscribe(ConfigurationLink link, Echoarea area, long last) throws Exception {
        Subscription subscription = new Subscription();
        subscription.setConfigurationLink(link);
        subscription.setEchoarea(area);
        subscription.setLastMessage(last);
        subscription.setAccessLevel(AccessLevel.BOTH);
        database.getDao(Subscription.class).create(subscription);
    }

    private void message(Echoarea area, long id, String subject) throws Exception {
        Echomail message = new Echomail();
        message.setId(id);
        message.setArea(area);
        message.setMsgId(subject);
        message.setFromName("from");
        message.setToName("to");
        message.setFromAddr("2:467/1");
        message.setDate(new Date());
        message.setSubject(subject);
        message.setText("body");
        message.setSeenBy("467/1");
        message.setPath("467/1");
        database.getDao(Echomail.class).create(message);
    }

    private List<String> subjects(Iterator<Message> iterator) {
        List<String> subjects = new ArrayList<>();
        while (iterator.hasNext()) {
            subjects.add(iterator.next().getSubject());
        }
        return subjects;
    }

    @Test
    public void appliesEachAreasLastMessageIndependently() {
        assertEquals(Arrays.asList("new-b", "new-a"), subjects(manager.getMessages(link)));
    }

    @Test
    public void singleAreaDoesNotUseOtherSubscriptionsThresholds() {
        assertEquals(Collections.singletonList("new-a"), subjects(manager.getMessages(link, "a")));
        assertTrue(subjects(manager.getMessages(link, "missing")).isEmpty());
    }
}
