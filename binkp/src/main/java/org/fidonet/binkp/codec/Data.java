package org.fidonet.binkp.codec;

import org.fidonet.binkp.io.BinkFrame;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 7:11 PM
 */
public interface Data {
    public BinkFrame getRawData();
    public boolean isCommand();
}
