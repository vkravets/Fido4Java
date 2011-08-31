from org.fidonet.jftn.share import CommandInterpreter
from java.lang import System

class TestCommand:

    def execute(self, param):
        global jftn;
        jftn.setVar("testVar")


CommandInterpreter.registerCommand("test", TestCommand())

