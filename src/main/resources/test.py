from org.fidonet.jftn.share import CommandInterpreter

class TossCommand:

    def execute(self, param):
        print("Test")

CommandInterpreter.registerCommand("test", TossCommand())

