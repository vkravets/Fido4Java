from java.lang import System

class TossCommand:

    def execute(self, param):
        return param

jftn.registerCommand("test", TossCommand())

