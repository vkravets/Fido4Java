from org.fidonet.jftn.share import CommandInterpreter
from java.lang import System

class TestCommand:

    def execute(self, param):
        global testScriptVar;
        testScriptVar.setVar("testVar")


jftn.registerCommand("test", TestCommand())

