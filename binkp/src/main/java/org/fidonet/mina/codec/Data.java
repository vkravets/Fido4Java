package org.fidonet.mina.codec;

import org.fidonet.mina.io.BinkFrame;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 7:11 PM
 */
public interface Data {
    public BinkFrame getRawData() throws NotImplementedException;
    public boolean isCommand();
}
