JFtn v0.1
=========

[![Build Status](https://www.cloudbees.com/sites/default/files/Button-Built-on-CB-1.png)](http://fido4java.ci.cloudbees.com/)

### BUILD STATUS: 
Travis: [![Build Status](https://secure.travis-ci.org/vkravets/Fido4Java.png?branch=master)](http://travis-ci.org/vkravets/Fido4Java)

Jenkins: [![Build Status](https://fido4java.ci.cloudbees.com/job/Fido4java/badge/icon)](https://fido4java.ci.cloudbees.com/job/Fido4java/)

Drone IO: [![Build Status](https://drone.io/github.com/vkravets/Fido4Java/status.png)](https://drone.io/github.com/vkravets/Fido4Java/latest)

BuildHive: [![Build Status](https://buildhive.cloudbees.com/job/vkravets/job/Fido4Java/badge/icon)](https://buildhive.cloudbees.com/job/vkravets/job/Fido4Java/)

Jenkins page: https://fido4java.ci.cloudbees.com/job/Fido4java/


### ABOUT
JFtn is a ftn-tosser written in Java. This is the fork from the origin http://sourceforge.net/projects/jftn/ project.
To use JFtn you need installed JVM with at least 1.6.
Feel free to report bugs and request for features.
Its very simple for now and can be used only for read-only system. It can toss netmail, have bugs,
and still under develompent.

### FEATURES
Can store echomail in jam-base.
Support several echo-links.
Store areas configuration in popular fidoconfig format. So you can use it with your favorite message editor.

### TODO

### INSTALL
To install simply copy the file jftn.jar to the desired location. Edit jftn.conf-dist to your liking and
save it in the same directory called jftn.conf.

### USAGE
Starting from the command line. The first parameter must be action. Action can be:

    help - print help for possible command-line options
    tosss - start tossing mail in inbound directory
    pull - start pulling the node via binkp.

### DEVELOPERS
For now there are a few developers (see below list), but you can join for develompent anytime.
You can always get a fresh version at GitHub: github.com/vkravets/Fido4Java.git or git://github.com/vkravets/Fido4Java.git

JFtn team members:

* Vladimir Kravets -- (vova.kravets<at>gmail.com or 2:467/70.113)
* Dmitriy Tochansky -- (tochansky<at>gmail.com or 2:5030/1111) original author of JFtn

Feel free to contact to us!
