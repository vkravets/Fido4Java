from org.fidonet.jftn.share import HookInterpreter
from org.fidonet.jftn.tosser import TossNetmainEvent

class TossEchoMailHook:

    def onEventHandle(self, param):
        print param.getMessage()
        print("TestHook")

HookInterpreter.registerHook(TossEchoMailEvent, TossEchoMailHook())

