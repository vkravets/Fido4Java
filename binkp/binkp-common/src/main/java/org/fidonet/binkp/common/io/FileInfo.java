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
    private long curSize;
    private long size;
    private long timestamp;
    private boolean finished;
    private boolean shouldSkip;

    private long offset;

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
        String[] tokens = commandArgs.split(" ");
        // Parse command args from M_FILE and M_GOT commands
        if (tokens.length >= 3) {
            String name = tokens[0].trim();
            long size = Long.valueOf(tokens[1].trim());
            long timestamp = Long.valueOf(tokens[2].trim());
            FileInfo info = new FileInfo(name, size, timestamp);
            // Check if command args get from M_FILE with offset
            if (tokens.length == 4) {
                long offset = Long.valueOf(tokens[3]);
                info.setOffset(offset);
            } else {
                info.setOffset(0);
            }
            return info;
        }
        throw new Exception("Invalid file info was passed");
    }
}
