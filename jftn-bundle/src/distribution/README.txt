JFtn standalone node

Windows: run jftn.cmd. PowerShell: run ./jftn.ps1.
Linux/macOS without bundled JRE: run ./jftn.sh.
Launchers use the bundled jre first, then JAVA_HOME, then java on PATH.
The no-jre package requires Java 17 or newer. Runtime packages are platform-specific.

On a clean installation, open the private setup URL printed in the console.
The wizard creates jftn.conf and the initial administrator account. Do not copy
jftn.conf.example unless configuring the node manually. Edit all example identity
and link values before using a manual configuration.

Working folders are created automatically on every startup if missing.
Libraries are in lib/, Groovy runtime JARs in lib/groovy/, plugin modules in plugins/.
Editable Groovy mail robots are in scripts/mail-scripts/ (on the launch classpath).
File-only plugin settings are in conf.d/; startup creates missing templates only.
Back up jftn.conf, conf.d/, database/ and mail/ before replacing application binaries.
Keep your own data/configuration when upgrading; unpack a new release separately.
See docs/distributions.md and docs/web-ui.md for details.
