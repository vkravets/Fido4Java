package org.fidonet.config;


import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/5/11
 * Time: 1:24 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class BaseConfig implements IConfig {

    private static ILogger logger = LoggerFactory.getLogger(BaseConfig.class.getName());

    private Map<String, List<String>> props;

    public BaseConfig() {
        props = new HashMap<String, List<String>>();
    }

    @Override
    public void load(InputStream stream) throws ParseConfigException {
        InputStreamReader reader = new InputStreamReader(stream);
        try {
            int strnum = 0;
            String str;
            BufferedReader file = new BufferedReader(reader);
            while ((str = file.readLine()) != null) {
                strnum++;
                if (str.contains("#")) {
                    str = str.substring(0, str.indexOf('#'));
                    str = str.trim();
                }
                if (str.length() == 0) {
                    continue;
                }
                if (!str.contains("=")) {
                    logger.debug("Error in config string " + strnum);
                    break;
                }
                Scanner scanner = new Scanner(str);
                scanner.useDelimiter("=");
                if (scanner.hasNext()){
                    String name = scanner.next();
                    String value = scanner.next();
                    setValue(name.trim().toLowerCase(), value.trim());
                }
            }

            stream.close();
            file.close();
        } catch (IOException e) {
            throw new ParseConfigException(e);
        }

    }

    @Override
    public void load(String fileName) throws ParseConfigException {
        try {
            load(new FileInputStream(fileName));
        } catch (FileNotFoundException e) {
            throw new ParseConfigException(e);
        }
    }

    private void setValue(String name, String value) {
        // TODO: validate name and value
        if (validateData(name, value)) {
            List<String> values = props.get(name);
            if (values == null) {
                values = new ArrayList<String>(1);
                props.put(name, values);
            }
            values.add(value);
        }
    }

    private Boolean validateData(String name, String value) {
        return true;
    }

    @Override
    public String getValue(String key) {
        return this.getValue(key, null);
    }

    @Override
    public String getValue(String key, String defaultValue) {
        List<String> values = getValuesAsList(key);
        return (values == null) ? defaultValue : values.get(0);
    }

    @Override
    public List<String> getValuesAsList(String key) {
        List<String> values = props.get(key.trim().toLowerCase());
        if ((values == null) || (values.size() == 0)) {
            return null;
        } else {
            return new ArrayList<String>(values);
        }
    }

}
