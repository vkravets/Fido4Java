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

package org.fidonet.binkp.common.io;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/20/12
 * Time: 8:26 PM
 */
public class FileInfo {

    private String name;
    private volatile long curSize;
    private long size;
    private long timestamp;
    private volatile boolean finished;
    private volatile boolean shouldSkip;

    private volatile long offset;

    public FileInfo(String name, long size, long timestamp) {
        this.name = name;
        this.size = size;
        this.timestamp = timestamp;
        this.finished = false;
        this.shouldSkip = false;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public long getCurSize() {
        return curSize;
    }

    public void setCurSize(long curSize) {
        this.curSize = curSize;
    }

    public synchronized boolean isShouldSkip() {
        return shouldSkip;
    }

    public synchronized void setSkip(boolean shouldSkip) {
        this.shouldSkip = shouldSkip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileInfo fileInfo = (FileInfo) o;

        if (size != fileInfo.size) return false;
        if (timestamp != fileInfo.timestamp) return false;
        if (!name.equals(fileInfo.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) (size ^ (size >>> 32));
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "name='" + name + '\'' +
                ", curSize=" + curSize +
                ", size=" + size +
                ", timestamp=" + timestamp +
                ", finished=" + finished +
                ", offset=" + offset +
                '}';
    }

    public static FileInfo parseFileInfo(String commandArgs) throws Exception {
        String[] tokens = commandArgs.trim().split("\\s+");
        // Parse command args from M_FILE and M_GOT commands
        if (tokens.length == 3 || tokens.length == 4) {
            String name = unescapeName(tokens[0]);
            long size = Long.parseLong(tokens[1].trim());
            long timestamp = Long.parseLong(tokens[2].trim());
            if (name.isEmpty() || size < 0) throw new IllegalArgumentException("Invalid file metadata");
            FileInfo info = new FileInfo(name, size, timestamp);
            // Check if command args get from M_FILE with offset
            if (tokens.length == 4) {
                long offset = Long.parseLong(tokens[3]);
                info.setOffset(offset);
            } else {
                info.setOffset(0);
            }
            return info;
        }
        throw new Exception("Invalid file info was passed");
    }

    public String arguments() {
        return escapeName(name) + " " + size + " " + timestamp;
    }

    public static String escapeName(String name) {
        StringBuilder out = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c <= 32 || c == '\\' || c == 127) out.append(String.format("\\x%02x", (int)c));
            else out.append(c);
        }
        return out.toString();
    }

    public static String unescapeName(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\\') {
                int start = i + 1;
                if (start < name.length() && name.charAt(start) == 'x') start++;
                if (start + 2 > name.length()) throw new IllegalArgumentException("Invalid filename escape");
                try { c = (char)Integer.parseInt(name.substring(start, start + 2), 16); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid filename escape", e); }
                i = start + 1;
            }
            if (c == 0) throw new IllegalArgumentException("NUL in filename");
            out.append(c);
        }
        return out.toString();
    }
}
