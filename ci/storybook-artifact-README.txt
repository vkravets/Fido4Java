Viewing the JFtn component catalogue
===================================

Extract the entire storybook artifact, keeping assets and other subdirectories.
Opening index.html directly with file:// does not work: Storybook needs HTTP.

From the extracted directory (the one containing index.html and iframe.html),
start a static HTTP server. Python 3 is sufficient; npm install is not needed.

Windows PowerShell:
    py -3 -m http.server 6006 --bind 127.0.0.1

Linux/macOS:
    python3 -m http.server 6006 --bind 127.0.0.1

Open http://127.0.0.1:6006/ in a browser. Leave the terminal running; Ctrl+C stops
the server. If the port is occupied, choose another port in both command and URL.
Python 3 must be installed or its executable supplied by an absolute path.

If you have the JFtn source checkout, you can also serve this extracted directory
through Gradle, without Python or a system Node installation when a portable
Node is already present under the checkout's build/ directory:
    .\gradlew.bat :web-ui:previewStorybook "-PstorybookDirectory=C:/Downloads/storybook"
Run this command from the repository root; use an absolute artifact path.
For live editing of component sources instead, use :web-ui:storybook. Its isolated
sample data does not require the Thrift compiler or generated browser bindings.

Use the sidebar to select a form or state. The toolbar selects light/dark and
English/Ukrainian. These are isolated component examples, not a running node.

The separate vrt-baselines artifact contains the committed expected screenshots,
under desktop/ and mobile/. They are available even when VRT has no mismatches.
VRT failure reports and actual/expected/diff images are in vrt-reports.
