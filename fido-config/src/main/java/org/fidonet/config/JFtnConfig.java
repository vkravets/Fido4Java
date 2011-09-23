package org.fidonet.config;

import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.validators.ConfigValidator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/2/11
 * Time: 2:13 AM
 * To change this template use File | Settings | File Templates.
 */
public class JFtnConfig extends BaseConfig {

    private ILogger logger = LoggerFactory.getLogger(JFtnConfig.class.getName());

    private Map<String, Link> links;

    public JFtnConfig() {
        links = new HashMap<String, Link>();
    }

    private class JFtnConfigValidator implements ConfigValidator<JFtnConfig> {

        @Override
        public boolean isValidate(JFtnConfig config) {
            return config.getValue("Inbound") != null &&
                    config.getValue("Tmp") != null &&
                    config.getValue("EchoPath") != null &&
                    config.getValue("Link") != null &&
                    config.getValue("SysOp") != null &&
                    config.getValue("Outbound") != null;
        }
    }

    @Override
    public boolean isValidate() {
        JFtnConfigValidator validator = new JFtnConfigValidator();
        return validator.isValidate(this);
    }

    @Override
    public void load(InputStream stream) throws ParseConfigException {
        super.load(stream);
        if (isValidate()) {
            links = getLinks(getValuesAsList("link"));
        } else {
            throw new ParseConfigException("Config is not valid!");
        }
    }

    private Map<String, Link> getLinks(List<String> links) {
        Map<String, Link> result = new HashMap<String, Link>();
        for (String linkStr : links) {
            Link link = new Link(linkStr);
            result.put(link.getAddr().toString(), link);
        }
        return result;
    }

    public Link getLink(FTNAddr addr) {
        return links.get(addr.toString());
    }

    public String getArealistFile() {
        String areaList = getValue("AreaListFile", "areas");
        boolean created = false;
        try {
            File areasFile = new File(areaList);
            if (!areasFile.exists()) {
                created = areasFile.createNewFile();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return areaList;
    }

    public String getEchoPath() {
        String echoPath = getValue("EchoPath");
        File echoPathDir = new File(echoPath);
        if (!echoPathDir.exists()) {
            logger.warn("Echo path is not exists. Will be created...");
            echoPathDir.mkdirs();
        }
        return echoPath;
    }

    public Integer isDeleteTossedFiles() {
        return Integer.valueOf(getValue("Deletetossed", "1"));
    }

    public String getTmpDir() {
        String sysTemp = System.getenv("TEMP");
        String tmpDir = getValue("Tmp");
        if (tmpDir == null) {
            if (sysTemp != null) {
                tmpDir = sysTemp + System.getProperty("file.separator") + "jtoss";
                File systemTemp = new File(tmpDir);
                if (!systemTemp.exists()) {
                    systemTemp.mkdirs();
                }
            } else {
                tmpDir = "./temp";
            }
        }
//        if (tmpDir == null) {
//            tmpDir = "temp";
//        }
        File tmp = new File(tmpDir);
        if (!tmp.exists()) {
            logger.warn("Temp folder was not exists. Will be created...");
            tmp.mkdirs();
        }
        try {
            return tmp.getCanonicalPath() + System.getProperty("file.separator");
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return tmpDir + System.getProperty("file.separator");
        }
    }

    public String getOutbound() {
        return getValue("outbound");
    }

}
