package org.fidonet.mina;

import org.fidonet.binkp.StationConfig;
import org.fidonet.mina.io.FileInfo;
import org.fidonet.types.Link;

import java.io.File;
import java.io.FilenameFilter;

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
        link.setBoxPath("/home/sly-arch/work/my/fido/temp/2");
        Client client = new Client(link);
        StationConfig config = new StationConfig("Test", "Test Test", "Ukraine", "ndl", "2:467/111.1@fidonet.org");
        SessionContext context = new SessionContext(config, link);

        File dirBox = new File(link.getBoxPath());
        String[] files = dirBox.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                File file = new File(dir.getPath() + File.separator + name);
                return !file.isDirectory();
            }
        });

        long total = 0;
        for (String file : files) {
            File fileObject = new File(dirBox.getPath() + File.separator + file);
            FileInfo fileInfo = new FileInfo(fileObject.getName(), fileObject.length(), fileObject.lastModified());
            context.getSendFiles().addFirst(fileInfo);
            total += fileObject.length();
        }
        context.setSendFilesSize(total);

        context.setServerRole(ServerRole.CLIENT);
        client.connect(context);

        if (client.isConnect()) {
            try {
                for (;;) {
                    Thread.sleep(1000);
                    if (context.isReceivingIsFinish() && context.isSendingIsFinish()) {
                        break;
                    }
                }
            } finally {
                client.close();
            }
        } else {
            client.close();
        }

    }

}
