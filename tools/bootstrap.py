#!/usr/bin/env python3
"""Prepare project-local development tools from reviewed URLs and SHA-256 digests."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shlex
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import urllib.parse
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "tools/development-tools.lock.json"


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def platform_key(system=None, machine=None):
    systems = {"Windows": "windows", "Linux": "linux", "Darwin": "macos"}
    architectures = {"amd64": "x64", "x86_64": "x64", "arm64": "arm64", "aarch64": "arm64"}
    os_name = systems.get(system or platform.system())
    architecture = architectures.get((machine or platform.machine()).lower())
    if os_name is None or architecture is None:
        raise RuntimeError("Unsupported host; use a platform listed in the development tool lock")
    return f"{os_name}-{architecture}"


def run(command, cwd=ROOT, env=None, capture=False):
    options = {"cwd": cwd, "env": env, "check": True, "text": True, "timeout": 900}
    if capture:
        options.update(stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if os.name == "nt":
        options["creationflags"] = subprocess.CREATE_NO_WINDOW
    result = subprocess.run([str(item) for item in command], **options)
    return result.stdout.strip() if capture else None


def archive_name(asset):
    name = Path(urllib.parse.unquote(urllib.parse.urlparse(asset["url"]).path)).name
    if not name or not re.fullmatch(r"[0-9a-f]{64}", asset["sha256"]):
        raise ValueError("Invalid archive name or SHA-256 in development tool lock")
    return name


def obtain(asset, downloads, offline=False, legacy=()):
    """Verify cached inputs too; never execute or extract a corrupt cached artifact."""
    name = archive_name(asset)
    downloads.mkdir(parents=True, exist_ok=True)
    destination = downloads / name
    if destination.is_file():
        if digest(destination) != asset["sha256"]:
            raise RuntimeError(f"SHA-256 mismatch in {destination}; move it aside and retry")
        print(f"Verified cached {name}")
        return destination
    for candidate in legacy:
        if candidate.is_file() and digest(candidate) == asset["sha256"]:
            shutil.copyfile(candidate, destination)
            print(f"Reused verified local {candidate.name}")
            return destination
    if offline:
        raise RuntimeError(f"Offline input missing: {destination}")
    if urllib.parse.urlparse(asset["url"]).scheme != "https":
        raise ValueError("Tool downloads require HTTPS")
    print(f"Downloading {name}")
    request = urllib.request.Request(asset["url"], headers={"User-Agent": "JFtn-development-bootstrap"})
    with tempfile.NamedTemporaryFile(dir=downloads, suffix=".part", delete=False) as temporary:
        stage = Path(temporary.name)
    try:
        with urllib.request.urlopen(request, timeout=60) as response, stage.open("wb") as output:
            if urllib.parse.urlparse(response.geturl()).scheme != "https":
                raise RuntimeError("Rejected a tool download redirected away from HTTPS")
            shutil.copyfileobj(response, output, 1024 * 1024)
        if digest(stage) != asset["sha256"]:
            raise RuntimeError(f"SHA-256 mismatch downloading {name}")
        stage.replace(destination)
        return destination
    finally:
        stage.unlink(missing_ok=True)


def zip_member_path(name):
    path = PurePosixPath(name.replace("\\", "/"))
    if path.is_absolute() or ".." in path.parts or any(":" in part for part in path.parts):
        raise ValueError("Unsafe ZIP member path")
    return path


def unpack(archive, directory):
    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive) as source:
            for member in source.infolist():
                zip_member_path(member.filename)
                if stat.S_ISLNK(member.external_attr >> 16):
                    raise ValueError("ZIP symlinks are not supported")
            source.extractall(directory)
        return
    with tarfile.open(archive) as source:
        # Python's data filter rejects path/link escapes and devices while allowing
        # the internal npm/JDK symlinks present in official Unix tool archives.
        source.extractall(directory, filter="data")


def install_archive(archive, destination, sha256):
    marker = destination / ".jftn-tool.json"
    if destination.exists():
        if marker.is_file() and json.loads(marker.read_text(encoding="utf-8"))["sha256"] == sha256:
            return destination
        raise RuntimeError(f"Unmanaged or differently pinned tool directory: {destination}; move it aside")
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Staging and cleanup are confined to this newly created project-local directory.
    with tempfile.TemporaryDirectory(prefix=".stage-", dir=destination.parent) as temporary:
        payload = Path(temporary) / "payload"
        payload.mkdir()
        unpack(archive, payload)
        entries = list(payload.iterdir())
        source = entries[0] if len(entries) == 1 and entries[0].is_dir() else payload
        (source / marker.name).write_text(json.dumps({"sha256": sha256}) + "\n", encoding="utf-8")
        source.replace(destination)
    return destination


def locate_system_java():
    configured = os.environ.get("JAVA_HOME")
    binary = "java.exe" if os.name == "nt" else "java"
    home = Path(configured) if configured else None
    if home is None:
        java = shutil.which("java")
        if java:
            home = Path(java).resolve().parent.parent
    if home is None or not (home / "bin" / binary).is_file():
        raise RuntimeError("--system-java requires JAVA_HOME or a JDK on PATH")
    compiler = "javac.exe" if os.name == "nt" else "javac"
    if not (home / "bin" / compiler).is_file():
        raise RuntimeError("A full JDK, including javac, is required")
    return home.resolve()


def prepare_tools(manifest, state, downloads, key, offline, system_java):
    if key not in manifest["node"]["assets"] or key not in manifest["jdk"]["assets"]:
        raise RuntimeError(f"No complete pinned tool profile for {key}")

    def prepare(asset, folder):
        name = archive_name(asset)
        archive = obtain(asset, downloads, offline, [ROOT / "build" / name])
        return install_archive(archive, state / folder, asset["sha256"])

    node_home = prepare(manifest["node"]["assets"][key], "node")
    if system_java:
        java_home = locate_system_java()
    else:
        asset = manifest["jdk"]["assets"][key]
        java_home = prepare(asset, "jdk") / asset["home"]
    thrift_home = prepare(manifest["thrift"]["source"], "thrift-source")
    if key == "windows-x64":
        asset = manifest["thrift"][key]
        executable = obtain(asset, downloads, offline, [ROOT / "build" / archive_name(asset)])
        thrift = state / "thrift.exe"
        shutil.copyfile(executable, thrift)
    else:
        thrift = thrift_home / "compiler/cpp/thrift"
        if not thrift.is_file():
            required = ["make", "c++", "bison", "flex", "pkg-config"]
            missing = [name for name in required if shutil.which(name) is None]
            if missing:
                raise RuntimeError("Install native Thrift build prerequisites first: " + ", ".join(missing))
            run(["sh", "configure", "--disable-libs", "--disable-tests", "--disable-tutorial"], cwd=thrift_home)
            run(["make", "-C", "compiler/cpp", "-j2"], cwd=thrift_home)
    node = node_home / ("node.exe" if os.name == "nt" else "bin/node")
    return {"node": node, "javaHome": java_home, "thrift": thrift,
            "runtime": thrift_home / "lib/js/src/thrift.js"}


def tool_versions(tools, manifest, system_java):
    java = tools["javaHome"] / "bin" / ("java.exe" if os.name == "nt" else "java")
    results = {
        "node": run([tools["node"], "--version"], capture=True),
        "java": run([java, "-version"], capture=True),
        "thrift": run([tools["thrift"], "-version"], capture=True),
        "python": platform.python_version(),
    }
    if results["node"] != "v" + manifest["node"]["version"]:
        raise RuntimeError("Installed Node version does not match the lock")
    if "Thrift version " + manifest["thrift"]["version"] not in results["thrift"]:
        raise RuntimeError("Installed Thrift version does not match the contracts")
    match = re.search(r'version "(\d+)', results["java"])
    if match is None or int(match.group(1)) < 17:
        raise RuntimeError("Gradle development requires JDK 17 or newer")
    expected = manifest["jdk"]["version"].split("+")[0]
    if not system_java and expected not in results["java"]:
        raise RuntimeError("Installed JDK version does not match the lock")
    if not tools["runtime"].is_file():
        raise RuntimeError("Official Thrift browser runtime is missing")
    return results


def environment(tools, state):
    env = os.environ.copy()
    env.update({
        "JAVA_HOME": str(tools["javaHome"]),
        "GRADLE_USER_HOME": str(ROOT / ".gradle-user-home"),
        "THRIFT_EXECUTABLE": str(tools["thrift"]),
        "THRIFT_BROWSER_RUNTIME": str(tools["runtime"]),
        "PLAYWRIGHT_BROWSERS_PATH": str(state / "playwright-browsers"),
        "npm_config_cache": str(ROOT / ".gradle-user-home/npm-cache"),
        "ORG_GRADLE_PROJECT_nodeExecutable": str(tools["node"]),
        "ORG_GRADLE_PROJECT_thriftExecutable": str(tools["thrift"]),
        "ORG_GRADLE_PROJECT_thriftBrowserRuntime": str(tools["runtime"]),
    })
    directories = [str(tools["javaHome"] / "bin"), str(tools["node"].parent), str(tools["thrift"].parent)]
    env["PATH"] = os.pathsep.join(directories + [env.get("PATH", "")])
    return env


def environment_expression(path, shell):
    try:
        relative = path.resolve().relative_to(ROOT.resolve()).as_posix()
    except ValueError:
        return "'" + str(path).replace("'", "''") + "'" if shell == "ps" else shlex.quote(str(path))
    if shell == "ps":
        return "(Join-Path $jftnDevelopmentRoot '" + relative + "')"
    return '"$JFTN_DEVELOPMENT_ROOT/' + relative + '"'


def write_environment(tools, state):
    env = environment(tools, state)
    names = ["JAVA_HOME", "GRADLE_USER_HOME", "THRIFT_EXECUTABLE", "THRIFT_BROWSER_RUNTIME",
             "PLAYWRIGHT_BROWSERS_PATH", "npm_config_cache", "ORG_GRADLE_PROJECT_nodeExecutable",
             "ORG_GRADLE_PROJECT_thriftExecutable", "ORG_GRADLE_PROJECT_thriftBrowserRuntime"]
    powershell = ["# Generated by tools/bootstrap.py; dot-source in your current session.",
                  "$jftnDevelopmentRoot = Split-Path -Parent $PSScriptRoot"]
    bash = ["#!/usr/bin/env bash", "# Generated; source this file in Bash.",
            'JFTN_DEVELOPMENT_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)']
    for name in names:
        powershell.append(f"$env:{name} = {environment_expression(Path(env[name]), 'ps')}")
        bash.append(f"export {name}={environment_expression(Path(env[name]), 'sh')}")
    powershell.append('$env:PATH = (Join-Path $env:JAVA_HOME \'bin\') + [IO.Path]::PathSeparator + '
                      '(Split-Path -Parent $env:ORG_GRADLE_PROJECT_nodeExecutable) + [IO.Path]::PathSeparator + '
                      '(Split-Path -Parent $env:THRIFT_EXECUTABLE) + [IO.Path]::PathSeparator + $env:PATH')
    bash.append('export PATH="$JAVA_HOME/bin:$(dirname -- "$ORG_GRADLE_PROJECT_nodeExecutable"):$(dirname -- "$THRIFT_EXECUTABLE"):$PATH"')
    (state / "dev-env.ps1").write_text("\n".join(powershell) + "\n", encoding="utf-8-sig")
    (state / "dev-env.sh").write_text("\n".join(bash) + "\n", encoding="utf-8")


def frontend(tools, state, offline, browsers):
    directory = ROOT / "web-ui/frontend"
    env = environment(tools, state)
    npm = tools["node"].parent / "node_modules/npm/bin/npm-cli.js"
    if os.name != "nt":
        npm = tools["node"].parent.parent / "lib/node_modules/npm/bin/npm-cli.js"
    command = [tools["node"], npm, "ci", "--no-audit", "--no-fund"]
    if offline:
        command.append("--offline")
    run(command, cwd=directory, env=env)
    if browsers:
        if offline:
            raise RuntimeError("Browser downloads need an online run; restore the browser cache for offline tests")
        run([tools["node"], directory / "node_modules/@playwright/test/cli.js", "install", "chromium"],
            cwd=directory, env=env)


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="Require verified cached archives; never download")
    parser.add_argument("--system-java", action="store_true", help="Use installed JDK 17+ instead of the pinned JDK")
    parser.add_argument("--frontend", action="store_true", help="Run npm ci using the pinned Node")
    parser.add_argument("--browsers", action="store_true", help="Also install local Chromium (implies --frontend)")
    parser.add_argument("--check", action="store_true", help="Inspect an existing bootstrap without installing")
    parser.add_argument("--download-dir", type=Path, help="Optional archive cache, including a restored offline cache")
    args = parser.parse_args(arguments)
    if sys.version_info < (3, 12):
        raise RuntimeError("Bootstrap needs Python 3.12+ for safe archive extraction")
    manifest = json.loads(LOCK.read_text(encoding="utf-8"))
    if manifest["schema"] != 1:
        raise ValueError("Unsupported development tool lock schema")
    state = ROOT / ".dev-tools"
    key = platform_key()
    if args.check:
        record = state / "environment.json"
        if not record.is_file():
            raise RuntimeError("No prepared environment; run tools/bootstrap first")
        saved = json.loads(record.read_text(encoding="utf-8"))
        if saved["platform"] != key or saved["lockSha256"] != digest(LOCK):
            raise RuntimeError("Environment platform or lock changed; rerun bootstrap")
        tools = {name: Path(value) for name, value in saved["tools"].items()}
        versions = tool_versions(tools, manifest, saved["systemJava"])
    else:
        state.mkdir(exist_ok=True)
        downloads = (args.download_dir or state / "downloads").resolve()
        tools = prepare_tools(manifest, state, downloads, key, args.offline, args.system_java)
        versions = tool_versions(tools, manifest, args.system_java)
        write_environment(tools, state)
        record = {"platform": key, "lockSha256": digest(LOCK), "systemJava": args.system_java,
                  "tools": {name: str(path.resolve()) for name, path in tools.items()}, "versions": versions}
        (state / "environment.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        if args.frontend or args.browsers:
            frontend(tools, state, args.offline, args.browsers)
    for name, version in versions.items():
        print(f"{name}: {version.splitlines()[0]}")
    print("Prepared. PowerShell: . ./.dev-tools/dev-env.ps1")
    print("Prepared. Bash: source .dev-tools/dev-env.sh")
    print("Optional container tests still require a working Docker/Podman daemon.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, tarfile.TarError,
            zipfile.BadZipFile) as failure:
        print(f"Bootstrap failed: {failure}", file=sys.stderr)
        sys.exit(1)
