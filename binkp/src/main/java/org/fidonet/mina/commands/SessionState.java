package org.fidonet.mina.commands;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:21 PM
 */
public class SessionState {
    private static final int STATE_WAITADDR = 1;
    private static final int STATE_WAITOK = 2;
    private static final int STATE_WAITPWD = 4;
    private static final int STATE_TRANSFER = 8;
    private static final int STATE_END = 16;
    private static final int STATE_ERR = 32;
}
