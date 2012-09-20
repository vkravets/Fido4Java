package org.fidonet.mina;

import org.fidonet.binkp.StationConfig;
import org.fidonet.mina.commands.SessionContext;
import org.fidonet.types.Link;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/20/12
 * Time: 10:30 AM
 */
public class Runner {

    public static void main(String[] argv) throws Exception {
        Link link = new Link("2:467/111,2:467/111.1,P@ssw0rd,localhost");
        Client client = new Client(link);
        StationConfig config = new StationConfig("Test", "Test Test", "Ukraine", "ndl", "2:467/111.1@fidonet.org");
        SessionContext context = new SessionContext(config, link);
        client.connect(context);

        try {
            for (;;) {
                Thread.sleep(1000);
            }
        } finally {
            client.disconnect();
        }

    }

}
