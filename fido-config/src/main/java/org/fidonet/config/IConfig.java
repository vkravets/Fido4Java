package org.fidonet.config;

import java.io.InputStream;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/2/11
 * Time: 2:08 AM
 * To change this template use File | Settings | File Templates.
 */
public interface IConfig {

    public void load(String fileName) throws ParseConfigException;

    public void load(InputStream stream) throws ParseConfigException;

    public String getValue(String key);

    public String getValue(String key, String defaultValue);

    public List<String> getValuesAsList(String key);

}
