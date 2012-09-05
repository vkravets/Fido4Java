import org.fidonet.jftn.share.Command

class TossCommand implements Command<String[], String[]> {

    @Override
    String[] execute(String[] argv) {
        def test = new TestModule()
        test.testModule()
        return argv  //To change body of implemented methods use File | Settings | File Templates.
    }
}

jftn.registerCommand("test", new TossCommand())

