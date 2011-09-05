import org.apache.log4j.Logger;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.protocol.binkp.BinkClient;

/**
 *
 * @author toch
 */
public class BinkD {

    private static Logger logger = Logger.getLogger(BinkD.class);

    public static void main(String[] args) throws ParseConfigException {
        JFtnConfig jftnConfig = new JFtnConfig();
        jftnConfig.load("jftn.conf");
        BinkClient cli = new BinkClient(jftnConfig);
        Thread clientthread = new Thread(cli);
        clientthread.start();
        try {
            clientthread.join();
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
        }

    }
    
}
