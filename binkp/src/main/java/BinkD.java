import org.fidonet.config.IConfig;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.protocol.binkp.BinkClient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author toch
 */
public class BinkD {

    public static void main(String[] args) throws ParseConfigException {
        IConfig jftnConfig = new JFtnConfig();
        jftnConfig.load("jftn.conf");
        BinkClient cli = new BinkClient(jftnConfig);
        Thread clientthread = new Thread(cli);
        clientthread.start();
        try {
            clientthread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(BinkD.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
}
