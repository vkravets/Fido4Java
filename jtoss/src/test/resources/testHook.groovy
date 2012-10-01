import org.fidonet.events.Event
import org.fidonet.jftn.share.Hook
import org.fidonet.tests.share.TestEvent

class TestHook implements Hook {

    @Override
    void onEventHandle(Event event) {
        def myEvent = (TestEvent) event;
        def str = String.format("TestHook %s", myEvent.getParam())
        System.out.print(str)
    }
}

jftn.registerHook(TestEvent, new TestHook())

