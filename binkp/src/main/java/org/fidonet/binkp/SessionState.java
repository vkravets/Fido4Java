package org.fidonet.binkp;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 2:21 PM
 */
public enum SessionState {
    STATE_WAITOK, // State for client role - waiting authorization answer
    STATE_WAITPWD, // State for server role - waiting authorization step
    STATE_IDLE,
    STATE_TRANSFER, // State for client/server role - begin transfer phase
    STATE_BSY, // State for client/server role - if server is busy.
    STATE_END, // State for ending session (all files was recivied, all files was sent)
    STATE_ERR // State if error was happen, meaning to end session with client/server
}
