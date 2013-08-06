package org.fidonet.jftn.scheduler;

import junit.framework.TestCase;
import org.fidonet.jftn.plugins.PluginException;
import org.fidonet.jftn.plugins.PluginManager;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vladimir.kravets-ukr@hp.com
 * Date: 8/6/13
 * Time: 4:01 PM
 */
public class SchedulerPluginTest {
    
    @Test
    @Ignore
    public void pluginTest() {
        PluginManager manager = PluginManager.getInstance();
        manager.loadPlugins();
        boolean exception = false;
        Scheduler schedule = null;
        try {
            schedule = manager.getContext("schedule");
        } catch (PluginException ex) {
            exception = true;
            ex.printStackTrace();
        } catch (ClassCastException ex) {
            exception = true;
            ex.printStackTrace();
        }
        TestCase.assertEquals(false, exception);
        TestCase.assertNotNull(schedule);
        
        schedule.start();
        final Boolean[] taskExecute = {false};
        schedule.schedule("* * * * *", new Runnable() {
            @Override
            public void run() {
                taskExecute[0] = true;
            }
        });
        try {
            Thread.sleep(1000*61);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            schedule.stop();
            PluginManager.getInstance().unloadPlugins();
        }
        TestCase.assertEquals(Boolean.TRUE, taskExecute[0]);
    }
}
