package org.fidonet.validators;

import org.fidonet.config.IConfig;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/2/11
 * Time: 2:41 AM
 * To change this template use File | Settings | File Templates.
 */
public interface ConfigValidator<T extends IConfig> {
    public boolean isValidate(T config);
}
