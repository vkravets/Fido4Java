"""Exercise macOS launcher policy with fake host commands, without installing tools."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GIT_BASH = Path("C:/Program Files/Git/bin/bash.exe")
BASH = str(GIT_BASH) if os.name == "nt" and GIT_BASH.is_file() else shutil.which("bash")


@unittest.skipUnless(BASH, "Bash is required for launcher tests")
class MacosBootstrapTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        tools = self.root / "tools"
        tools.mkdir()
        shutil.copyfile(ROOT / "tools/bootstrap-macos.sh", tools / "bootstrap-macos.sh")
        self.script(tools / "bootstrap.sh", 'printf "arg=<%s>\\n" "$@"\n'
                    'printf "python=<%s>\\ncpp=<%s>\\n" "$JFTN_BOOTSTRAP_PYTHON" "$CPPFLAGS"\n')
        self.script(self.bin / "uname", 'echo "${FAKE_SYSTEM:-Darwin}"\n')
        self.script(self.bin / "xcode-select", 'exit "${FAKE_XCODE_EXIT:-0}"\n')
        self.script(self.bin / "brew", '''case "$1" in
  list) exit "${FAKE_MISSING:-0}" ;;
  install) printf 'install=<%s>\n' "$*" ;;
  --prefix) printf '/fake brew/%s\n' "$2" ;;
  *) exit 2 ;;
esac
''')

    def script(self, path, content):
        path.write_text("#!/usr/bin/env bash\n" + content, encoding="utf-8", newline="\n")
        path.chmod(0o755)

    def launch(self, *arguments, **overrides):
        env = os.environ.copy()
        # Convert Windows paths inside Bash, rather than assuming a drive mount name.
        env.update(overrides)
        env.pop("JFTN_BOOTSTRAP_PYTHON", None)
        return subprocess.run(
            [BASH, "-c", 'export PATH="$(cd "$1/bin" && pwd):$PATH"; '
             'shift; exec bash "$@"', "test", self.root.as_posix(),
             (self.root / "tools/bootstrap-macos.sh").as_posix(), *arguments],
            env=env, capture_output=True, text=True, timeout=30)

    def test_forwarded_arguments_and_homebrew_paths_with_spaces(self):
        result = self.launch("--frontend", "--download-dir", "/cache with spaces", CPPFLAGS="-DKEEP")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("arg=</cache with spaces>", result.stdout)
        self.assertIn("python=</fake brew/python@3.13/bin/python3.13>", result.stdout)
        self.assertIn("cpp=<-I/fake brew/boost/include -DKEEP>", result.stdout)
        self.assertNotIn("install=<", result.stdout)

    def test_installed_missing_dependencies_only_when_requested(self):
        refused = self.launch(FAKE_MISSING="1")
        self.assertNotEqual(0, refused.returncode)
        self.assertIn("--install-prerequisites", refused.stderr)
        installed = self.launch("--install-prerequisites", "--check", FAKE_MISSING="1")
        self.assertEqual(0, installed.returncode, installed.stderr)
        self.assertIn("install=<install python@3.13 boost bison flex libtool pkg-config>", installed.stdout)
        self.assertIn("arg=<--check>", installed.stdout)
        self.assertNotIn("arg=<--install-prerequisites>", installed.stdout)

    def test_rejected_wrong_os_and_missing_command_line_tools(self):
        wrong_os = self.launch(FAKE_SYSTEM="Linux")
        self.assertNotEqual(0, wrong_os.returncode)
        self.assertIn("requires macOS", wrong_os.stderr)
        missing_tools = self.launch(FAKE_XCODE_EXIT="1")
        self.assertNotEqual(0, missing_tools.returncode)
        self.assertIn("xcode-select --install", missing_tools.stderr)
