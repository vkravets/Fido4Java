package org.fidonet.jftn.plugins.mock;

import org.fidonet.jftn.plugins.PluginInformation;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/7/13
 * Time: 1:43 PM
 */
public class PluginB extends PluginMock {
    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("B", 1, 0, "", "G");
    }
}
