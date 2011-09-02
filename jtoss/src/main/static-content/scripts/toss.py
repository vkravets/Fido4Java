from org.fidonet.jftn.tosser import Tosser

class TossCommand:

    def execute(self, param):
        tosser = Tosser()
        tosser.runFast(jftn.getConfig().getInbound())

jftn.registerCommand("tosspack", TossCommand())

