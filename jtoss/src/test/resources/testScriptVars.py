class TestCommand:
    def execute(self, param):
        global testScriptVar;
        testScriptVar.setVar("testVar")


jftn.registerCommand("test", TestCommand())

