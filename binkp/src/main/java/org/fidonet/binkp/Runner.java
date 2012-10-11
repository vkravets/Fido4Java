package org.fidonet.binkp;

import org.fidonet.types.Link;

import java.util.concurrent.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/20/12
 * Time: 10:30 AM
 */
public class Runner {

    private ExecutorService threadPoolExecutor;

    public Runner() {
        threadPoolExecutor = Executors.newCachedThreadPool();
    }

    public void poll(Link link, final SessionContext context) throws Exception {
        final Client client = new Client(link);
        Runnable clientRun = new Runnable() {

            public void waitSessionFinish () throws InterruptedException {
                for (;;) {
                    Thread.sleep(500);
                    if (context.isReceivingIsFinish() && context.isSendingIsFinish() ||
                            context.getState().equals(SessionState.STATE_ERR) ||
                            context.getState().equals(SessionState.STATE_END)) {
                        break;
                    }
                }
            }

            @Override
            public void run() {
                try {
                    client.run(context);
                    if (client.isConnect()) {
                        waitSessionFinish();
                    }
                } catch (Exception e) {
                    // todo logger
                } finally {
                    client.stop(context);
                }
            }
        };

        threadPoolExecutor.submit(clientRun);
    }

    public void bindServer(final SessionContext context, final int port) {
        Runnable runServer = new Runnable() {
            @Override
            public void run() {
                Server server = new Server(port);
                try {
                    server.run(context);
                } catch (Exception e) {
                    // todo log
                }
            }
        };
        threadPoolExecutor.submit(runServer);

    }

}
