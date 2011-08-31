from org.fidonet.tests.share import TestEvent
from java.lang import System

class TestHook:

    def onEventHandle(self, param):
        str = "TestHook %s" % param.getParam()
        System.out.print(str)

jftn.registerHook(TestEvent, TestHook())

