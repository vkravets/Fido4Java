/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

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
 */
public class JFtnConfig extends BaseConfig {

    private static final ILogger logger = LoggerFactory.getLogger(JFtnConfig.class.getName());

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

    public String getArealistFile() throws IOException {
        String areaList = getValue("AreaListFile", "areas");
        boolean created = false;
        File areasFile = new File(areaList);
        if (!areasFile.exists()) {
            areasFile.getParentFile().mkdirs();
            created = areasFile.createNewFile();
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
            logger.warn("Temp folder was not exists. Will created...");
            tmp.mkdirs();
        } else {
            try {
                return tmp.getCanonicalPath() + System.getProperty("file.separator");
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return tmpDir + System.getProperty("file.separator");
    }

    public String getOutbound() {
        return getValue("outbound");
    }

    public String getInbound() {
        return getValue("inbound");
    }

}
