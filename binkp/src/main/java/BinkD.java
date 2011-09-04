import java.util.logging.Level;
import java.util.logging.Logger;
import org.fidonet.protocol.binkp.BinkClient;

/**
 *
 * @author toch
 */
public class BinkD {

    public static void main(String[] args)
    {
        BinkClient cli = new BinkClient("jftn.conf");
        Thread clientthread = new Thread(cli);
        clientthread.start();
        try {
            clientthread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(BinkD.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
}
