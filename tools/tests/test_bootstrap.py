import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location("jftn_bootstrap", Path(__file__).resolve().parents[1] / "bootstrap.py")
bootstrap = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bootstrap)


class BootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def zip(self, filename="tool/bin.txt", data=b"verified input"):
        import zipfile
        path = self.root / "tool.zip"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(filename, data)
        return path

    def asset(self, archive):
        return {"url": "https://example.invalid/" + archive.name, "sha256": bootstrap.digest(archive)}

    def test_reused_verified_legacy_archive_and_installed_idempotently_without_network(self):
        archive = self.zip()
        asset = self.asset(archive)
        cached = bootstrap.obtain(asset, self.root / "cache", offline=True, legacy=[archive])
        destination = self.root / "installed"
        bootstrap.install_archive(cached, destination, asset["sha256"])
        self.assertEqual(b"verified input", (destination / "bin.txt").read_bytes())
        bootstrap.install_archive(cached, destination, asset["sha256"])
        self.assertEqual(asset["sha256"], json.loads((destination / ".jftn-tool.json").read_text())["sha256"])

    def test_refused_corrupt_cached_archive_and_missing_offline_input(self):
        archive = self.zip()
        asset = self.asset(archive)
        cache = self.root / "cache"
        cache.mkdir()
        (cache / archive.name).write_bytes(b"corrupt")
        with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
            bootstrap.obtain(asset, cache, offline=True)
        with self.assertRaisesRegex(RuntimeError, "Offline input missing"):
            bootstrap.obtain(asset, self.root / "empty", offline=True)

    def test_rejected_zip_path_escapes_before_extracting_any_payload(self):
        for name in ["../escape", "/absolute", "C:/escape", "a/../../escape", "..\\escape"]:
            with self.subTest(name=name):
                archive = self.zip(name)
                destination = self.root / "payload"
                destination.mkdir(exist_ok=True)
                with self.assertRaises(ValueError):
                    bootstrap.unpack(archive, destination)
                self.assertEqual([], list(destination.iterdir()))

    def test_rejected_tar_link_escape(self):
        archive = self.root / "tool.tar.gz"
        with tarfile.open(archive, "w:gz") as output:
            member = tarfile.TarInfo("tool/escape")
            member.type = tarfile.SYMTYPE
            member.linkname = "../../outside"
            output.addfile(member)
        destination = self.root / "payload"
        destination.mkdir()
        with self.assertRaises(tarfile.FilterError):
            bootstrap.unpack(archive, destination)

    def test_extracted_normal_unix_archive(self):
        archive = self.root / "tool.tar.gz"
        with tarfile.open(archive, "w:gz") as output:
            member = tarfile.TarInfo("tool/bin/example")
            member.mode = 0o755
            member.size = 4
            output.addfile(member, io.BytesIO(b"data"))
        bootstrap.install_archive(archive, self.root / "installed", bootstrap.digest(archive))
        self.assertEqual(b"data", (self.root / "installed/bin/example").read_bytes())

    def test_did_not_overwrite_unmanaged_tools(self):
        archive = self.zip()
        destination = self.root / "installed"
        destination.mkdir()
        original = destination / "keep.txt"
        original.write_text("keep")
        with self.assertRaisesRegex(RuntimeError, "Unmanaged"):
            bootstrap.install_archive(archive, destination, bootstrap.digest(archive))
        self.assertEqual("keep", original.read_text())

    def test_mapped_supported_host_names_and_rejected_unknown_architectures(self):
        self.assertEqual("windows-x64", bootstrap.platform_key("Windows", "AMD64"))
        self.assertEqual("linux-arm64", bootstrap.platform_key("Linux", "aarch64"))
        self.assertEqual("macos-arm64", bootstrap.platform_key("Darwin", "arm64"))
        with self.assertRaises(RuntimeError):
            bootstrap.platform_key("Linux", "riscv64")

    def test_lock_contains_fixed_https_assets_and_matches_project_versions(self):
        manifest = json.loads(bootstrap.LOCK.read_text())
        self.assertEqual(1, manifest["schema"])
        for tool in ["node", "jdk"]:
            for asset in manifest[tool]["assets"].values():
                self.assertTrue(asset["url"].startswith("https://"))
                bootstrap.archive_name(asset)
        thrift = (bootstrap.ROOT / "rpc/build.gradle").read_text()
        self.assertIn("libthrift:" + manifest["thrift"]["version"], thrift)
        package = json.loads((bootstrap.ROOT / "web-ui/frontend/package.json").read_text())
        self.assertEqual(manifest["playwright"], package["devDependencies"]["@playwright/test"])

    def test_generated_profiles_include_gradle_overrides_and_project_local_caches(self):
        state = self.root / ".dev-tools"
        state.mkdir()
        tools = {"javaHome": self.root / "jdk", "node": self.root / "node/node.exe",
                 "thrift": self.root / "thrift.exe", "runtime": self.root / "thrift.js"}
        bootstrap.write_environment(tools, state)
        for name in ["dev-env.ps1", "dev-env.sh"]:
            text = (state / name).read_text(encoding="utf-8-sig")
            self.assertIn("ORG_GRADLE_PROJECT_thriftExecutable", text)
            self.assertIn("ORG_GRADLE_PROJECT_nodeExecutable", text)
            self.assertIn("THRIFT_BROWSER_RUNTIME", text)
            self.assertIn("GRADLE_USER_HOME", text)


if __name__ == "__main__":
    unittest.main()
