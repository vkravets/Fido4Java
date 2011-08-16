package binkp;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 02.08.11
 * Time: 16:12
 */
public class SessionResult {
    private int status;
    SessFile[] files;

    SessionResult() {
        status = 0;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public SessFile[] getFiles() {
        return files;
    }
}
