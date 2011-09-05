package org.fidonet.tests.toss;

import junit.framework.TestCase;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.echobase.EchoMgr;
import org.fidonet.jftn.tosser.Tosser;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/2/11
 * Time: 5:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestTosser {

    @Test
    public void testToss() {
        JFtnConfig config = new JFtnConfig();
        InputStream configStream = TestTosser.class.getClassLoader().getResourceAsStream("jftn.conf");

        try {
            config.load(configStream);
        } catch (ParseConfigException e) {
            TestCase.fail(e.getMessage());
        }

        try{
            Tosser tosser = new Tosser(config);
            tosser.runFast(config.getValue("Inbound"));
            EchoMgr echoMgr = tosser.getAreamgr();
            List<String> list = echoMgr.getEchos();
            TestCase.assertEquals(6, list.size());
            TestCase.assertEquals(true, echoMgr.isEchoExists("ru.anime"));
            TestCase.assertEquals(false, echoMgr.isEchoExists("ru.cracks"));
            // TODO: test echobase
        } catch (Exception e) {
            TestCase.fail();
        }



    }
}
