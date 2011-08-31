from java.lang import System

class TossCommand:

    def execute(self, param):
        printStr = "["
        for item in param:
            printStr = "%s%s, " % (printStr, item)
        printStr = "%s]" % printStr[0:len(printStr)-2]
        System.out.print("Test %s" % printStr)

jftn.registerCommand("test", TossCommand())

