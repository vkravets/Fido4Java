package org.fidonet.db.upgrade.version0;

import org.fidonet.db.OrmManager;
import org.fidonet.db.objects.Version;
import org.fidonet.echobase.AccessLevel;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class MigrationRegressionTest {
    private OrmManagerV0 old;
    private OrmManager target;
    private Echoarea area;
    private ConfigurationLink link;
    private Date date = new Date(123456000L);

    @Before public void prepare() throws Exception {
        old = new OrmManagerV0("jdbc:h2:mem:old" + System.nanoTime());
        target = new OrmManager("jdbc:h2:mem:new" + System.nanoTime());
        old.connect(); target.connect();
        Echoarea deleted = new Echoarea(); deleted.setName("deleted");
        old.getDao(Echoarea.class).create(deleted); old.getDao(Echoarea.class).delete(deleted);
        area = new Echoarea(); area.setName("area"); area.setDescription("description");
        old.getDao(Echoarea.class).create(area);
        ConfigurationLink deletedLink = makeLink("2:467/99");
        old.getDao(ConfigurationLink.class).create(deletedLink); old.getDao(ConfigurationLink.class).delete(deletedLink);
        link = makeLink("2:467/1"); old.getDao(ConfigurationLink.class).create(link);
        Subscription subscription = new Subscription(); subscription.setConfigurationLink(link);
        subscription.setEchoarea(area); subscription.setLastMessage(777L); subscription.setAccessLevel(AccessLevel.READ);
        old.getDao(Subscription.class).create(subscription);
        Netmail netmail = new Netmail(); netmail.setFromName("from"); netmail.setToName("to");
        netmail.setFromAddr("2:467/1"); netmail.setToAddr("2:467/2"); netmail.setSubject("net subject");
        netmail.setText("net body"); netmail.setDate(date); netmail.setRoute("2:467/3");
        netmail.setSend(false); netmail.setAttr(7); old.getDao(Netmail.class).create(netmail);
        for (int i = 0; i < 1005; i++) {
            Echomail message = new Echomail(); message.setArea(area); message.setMsgId("msg" + i);
            message.setFromName("from"); message.setToName("to"); message.setFromAddr("2:467/1");
            message.setToAddr("2:467/2"); message.setDate(date); message.setSubject("subject" + i);
            message.setText("body"); message.setOrigin("origin"); message.setSeenBy("467/1");
            message.setPath("467/2"); message.setAttr(5);
            Map<String, String> kludges = new HashMap<>(); kludges.put("CHRS", "UTF-8 4");
            message.setKludges(kludges); old.getDao(Echomail.class).create(message);
        }
        Version version = new Version(); version.setId(7L); version.setVersion(0L);
        old.getDao(Version.class).create(version);
    }
    private ConfigurationLink makeLink(String address) {
        ConfigurationLink link = new ConfigurationLink(); link.setAddress(address);
        link.setPassword("pass"); link.setPacket_password("packet"); link.setHost("host");
        link.setPort(24554); link.setFlags("flags"); return link;
    }
    @After public void close() { old.disconnect(); target.disconnect(); }

    @Test public void migratesAllPagesFieldsIdsAndForeignKeys() throws Exception {
        assertTrue(new UpgraderV0().upgrade(old, target));
        assertEquals(1005, target.getDao(org.fidonet.db.objects.Echomail.class).countOf());
        org.fidonet.db.objects.Echomail message = target.<org.fidonet.db.objects.Echomail, Object>getDao(
                org.fidonet.db.objects.Echomail.class).queryForId(1005L);
        assertNotNull(message); assertEquals("msg1004", message.getMsgId());
        assertEquals(area.getId(), message.getArea().getId());
        assertEquals("from", message.getFromName()); assertEquals("to", message.getToName());
        assertEquals("2:467/1", message.getFromAddr()); assertEquals("2:467/2", message.getToAddr());
        assertEquals(date, message.getDate()); assertEquals("subject1004", message.getSubject());
        assertEquals("\u0001CHRS:UTF-8 4\rbody", message.getText()); assertEquals("origin", message.getOrigin());
        assertEquals("467/1", message.getSeenBy()); assertEquals("467/2", message.getPath());
        assertEquals(5, message.getAttr());
        org.fidonet.db.objects.Subscription subscription = target.<org.fidonet.db.objects.Subscription, Object>getDao(
                org.fidonet.db.objects.Subscription.class).queryBuilder().queryForFirst();
        assertEquals(area.getId(), subscription.getEchoarea().getId());
        assertEquals(link.getId(), subscription.getConfigurationLink().getId());
        assertEquals(Long.valueOf(777), subscription.getLastMessage());
        assertEquals(AccessLevel.READ, subscription.getAccessLevel());
        assertEquals("area", subscription.getEchoarea().getName());
        assertEquals("description", subscription.getEchoarea().getDescription());
        assertEquals("pass", subscription.getConfigurationLink().getPassword());
        assertEquals("packet", subscription.getConfigurationLink().getPacket_password());
        assertEquals("host", subscription.getConfigurationLink().getHost());
        assertEquals(Integer.valueOf(24554), subscription.getConfigurationLink().getPort());
        assertEquals("flags", subscription.getConfigurationLink().getFlags());
        org.fidonet.db.objects.Netmail net = target.<org.fidonet.db.objects.Netmail, Object>getDao(
                org.fidonet.db.objects.Netmail.class).queryForId(1L);
        assertEquals("net subject", net.getSubject()); assertEquals("net body", net.getText());
        assertEquals("from", net.getFromName()); assertEquals("to", net.getToName());
        assertEquals("2:467/1", net.getFromAddr()); assertEquals("2:467/2", net.getToAddr());
        assertEquals(date, net.getDate()); assertEquals("2:467/3", net.getRoute());
        assertEquals(Boolean.FALSE, net.getSend()); assertEquals(Integer.valueOf(7), net.getAttr());
        assertEquals(Long.valueOf(0), target.<Version, Object>getDao(Version.class).queryForId(7L).getVersion());
        assertEquals(1005, old.getDao(Echomail.class).countOf());
        org.fidonet.db.objects.Echoarea added = new org.fidonet.db.objects.Echoarea(); added.setName("next");
        target.getDao(org.fidonet.db.objects.Echoarea.class).create(added);
        assertTrue(added.getId() > area.getId());
    }

    @Test public void rollsBackOnConflictingTargetRecords() throws Exception {
        org.fidonet.db.objects.Echoarea conflict = new org.fidonet.db.objects.Echoarea();
        conflict.setName("area"); target.getDao(org.fidonet.db.objects.Echoarea.class).create(conflict);
        assertFalse(new UpgraderV0().upgrade(old, target));
        assertEquals(1, target.getDao(org.fidonet.db.objects.Echoarea.class).countOf());
        assertEquals(0, target.getDao(org.fidonet.db.objects.ConfigurationLink.class).countOf());
        assertEquals(0, target.getDao(org.fidonet.db.objects.Echomail.class).countOf());
        assertEquals(1005, old.getDao(Echomail.class).countOf());
    }
}
