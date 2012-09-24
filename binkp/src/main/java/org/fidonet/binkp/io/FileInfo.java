package org.fidonet.binkp.io;

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

    private long offset;

    public FileInfo(String name, long size, long timestamp) {
        this.name = name;
        this.size = size;
        this.timestamp = timestamp;
        this.finished = false;
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
