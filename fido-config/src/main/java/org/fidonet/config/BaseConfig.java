/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/5/11
 * Time: 1:24 PM
 */
public abstract class BaseConfig implements IConfig {

    private static final Logger logger = LoggerFactory.getLogger(BaseConfig.class.getName());

    private Map<String, List<String>> props;

    public BaseConfig() {
        props = new HashMap<String, List<String>>();
    }

    @Override
    public void load(InputStream stream) throws ParseConfigException {
        try (BufferedReader file = new BufferedReader(new InputStreamReader(stream))) {
            int strnum = 0;
            String str;
            while ((str = file.readLine()) != null) {
                strnum++;
                if (str.contains("#")) {
                    str = str.substring(0, str.indexOf('#'));
                }
                str = str.trim();
                if (str.length() == 0) {
                    continue;
                }
                if (!str.contains("=")) {
                    throw new ParseConfigException("Missing '=' on configuration line " + strnum);
                }
                int separator = str.indexOf('=');
                String name = str.substring(0, separator).trim();
                String value = str.substring(separator + 1).trim();
                setValue(name.toLowerCase(Locale.ROOT), value);
            }

        } catch (IOException e) {
            throw new ParseConfigException(e);
        }
    }

    @Override
    public void load(String fileName) throws ParseConfigException {
        try (InputStream stream = new FileInputStream(fileName)) {
            load(stream);
        } catch (IOException e) {
            throw new ParseConfigException(e);
        }
    }

    private void setValue(String name, String value) {
        if (validateData(name, value)) {
            List<String> values = props.get(name);
            if (values == null) {
                values = new ArrayList<String>(1);
                props.put(name, values);
            }
            values.add(value);
        }
    }

    protected void applyOverrides(BaseConfig overrides) {
        overrides.props.forEach((key, values) -> props.put(key, new ArrayList<>(values)));
    }

    protected List<String> propertyNames() {
        return new ArrayList<>(props.keySet());
    }

    private Boolean validateData(String name, String value) {
        return (name.length() > 0) && (value.length() > 0);
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
        List<String> values = props.get(key.trim().toLowerCase(Locale.ROOT));
        if ((values == null) || (values.size() == 0)) {
            return null;
        } else {
            return new ArrayList<String>(values);
        }
    }
}
