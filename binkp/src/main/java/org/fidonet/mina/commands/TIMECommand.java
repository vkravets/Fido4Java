package org.fidonet.mina.commands;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/19/12
 * Time: 5:10 PM
 */
public class TIMECommand extends NULCommand{

    private static final DateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    @Override
    public String getArguments(SessionContext sessionContext) {
        return format.format(new Date());
    }

    @Override
    protected String getPrefix() {
        return "TIME";
    }
}
