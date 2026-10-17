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

import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.fidonet.validators.ConfigValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 9/2/11
 * Time: 2:13 AM
 */
public class JFtnConfig extends BaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(JFtnConfig.class.getName());

    private Map<String, Link> links;
    private Path sourcePath;
    private volatile JFtnConfig active;

    /** Parses a candidate and its fragments without changing the live configuration. */
    public static JFtnConfig candidate(Path source, byte[] bytes) throws Exception {
        JFtnConfig result = new JFtnConfig();
        result.load(new ByteArrayInputStream(bytes));
        result.sourcePath = source.toAbsolutePath().normalize();
        ConfigurationFragments.loadInto(result, result.sourcePath);
        result.links = result.getLinks(result.getValuesAsList("Link"));
        EchoAutoCreatePolicy.read(result);
        return result;
    }

    public Map<String, List<String>> options() {
        JFtnConfig current = active;
        if (current != null)
            return current.options();
        Map<String, List<String>> result = new HashMap<>();
        for (String name : propertyNames())
            result.put(name, getValuesAsList(name));
        return result;
    }

    /** Publishes a fully prepared snapshot. Callers serialize domain operations around this. */
    public void activate(JFtnConfig candidate) {
        if (candidate == this)
            throw new IllegalArgumentException("Cannot activate the live configuration itself");
        active = candidate;
    }

    @Override
    public List<String> getValuesAsList(String key) {
        JFtnConfig current = active;
        return current == null ? super.getValuesAsList(key) : current.getValuesAsList(key);
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    @Override
    public void load(String fileName) throws ParseConfigException {
        super.load(fileName);
        sourcePath = Path.of(fileName).toAbsolutePath().normalize();
        try {
            ConfigurationFragments.loadInto(this, sourcePath);
        } catch (IOException failure) {
            throw new ParseConfigException(failure);
        }
        links = getLinks(getValuesAsList("link"));
    }

    public JFtnConfig() {
        links = new HashMap<String, Link>();
    }

    private class JFtnConfigValidator implements ConfigValidator<JFtnConfig> {

        @Override
        public boolean isValidate(JFtnConfig config) {
            return config.getValue("Inbound") != null && config.getValue("Tmp") != null
                    && config.getValue("EchoPath") != null && config.getValue("Link") != null
                    && config.getValue("SysOp") != null && config.getValue("Outbound") != null;
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
        if (!isValidate()) {
            throw new ParseConfigException("Config is not valid!");
        }
        links = getLinks(getValuesAsList("link"));
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
        JFtnConfig current = active;
        if (current != null)
            return current.getLink(addr);
        return links.get(addr.toString());
    }

    public List<Link> getConfiguredLinks() {
        JFtnConfig current = active;
        if (current != null)
            return current.getConfiguredLinks();
        return new ArrayList<>(links.values());
    }

    public Set<FTNAddr> getLocalAddresses() {
        Set<FTNAddr> addresses = new LinkedHashSet<>();
        for (Link link : getConfiguredLinks()) {
            if (link.getMyaddr() != null) {
                addresses.addAll(link.getMyaddr());
            }
        }
        return addresses;
    }

    /** PacketPassword remote-address,password; defaults to the existing link password. */
    public String getPacketPassword(Link link) {
        List<String> values = getValuesAsList("PacketPassword");
        String password = link.getPass() == null ? "" : link.getPass();
        if (values != null) {
            for (String value : values) {
                password = packetPasswordOverride(value, link, password);
            }
        }
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(password) || password.length() > 8
                || password.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("PKT password must contain at most eight ASCII bytes: "
                    + link.getAddr());
        }
        return password;
    }

    private String packetPasswordOverride(String value, Link link, String current) {
        String[] fields = value.split(",", 2);
        if (fields.length != 2) {
            throw new IllegalArgumentException("Expected PacketPassword address,password");
        }
        if (!new FTNAddr(fields[0].trim()).equals(link.getAddr())) {
            return current;
        }
        String password = fields[1].trim();
        return password.equals("-") ? "" : password;
    }

    public String getArealistFile() throws IOException {
        String areaList = getValue("AreaListFile", "areas");
        boolean created = false;
        File areasFile = new File(areaList);
        if (!areasFile.exists()) {
            File parent = areasFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Unable to create area-list directory: " + parent);
            }
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

    public boolean isPreserveReceivedFiles() {
        return Boolean.parseBoolean(getValue("PreserveReceivedFiles", "true"));
    }

    public Path getReceivedArchivePath() {
        Path fallback = getInbound() == null
                ? Path.of(getTmpDir(), "received")
                : Path.of(getInbound()).toAbsolutePath().getParent().resolve("received");
        return Path.of(getValue("ReceivedArchivePath", fallback.toString()));
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
