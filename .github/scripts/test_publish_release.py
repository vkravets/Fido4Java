import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("publisher", Path(__file__).with_name("publish-release.py"))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)
REVISION = "a" * 40


class FakeGitHub:
    def __init__(self):
        self.release = None
        self.assets = {}
        self.calls = []
        self.tag_revision = REVISION

    def request(self, method, path, payload=None):
        self.calls.append((method, path, payload))
        if path.startswith("git/ref/tags/"):
            return {"object": {"type": "commit", "sha": self.tag_revision}}
        if path.startswith("releases/tags/"):
            return self.release
        if path.endswith("/assets?per_page=100"):
            return list(self.assets.values())
        if method == "POST" and path == "releases":
            self.release = {**payload, "id": 1, "html_url": "https://github.com/test/repo/releases/tag/v1.2.3"}
            return self.release
        if method == "PATCH" and path == "releases/1":
            self.release.update(payload)
            return self.release
        raise AssertionError(f"Unexpected call: {method} {path}")

    def upload(self, release_id, path):
        self.calls.append(("UPLOAD", path.name, None))
        asset = {"name": path.name, "digest": "sha256:" + publisher.digest(path), "state": "uploaded"}
        self.assets[path.name] = asset
        return asset


class PublishReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        lines = []
        for name in ("jftn-1.2.3-no-jre.zip", "jftn-1.2.3-linux-x64.zip",
                     "jftn-1.2.3-windows-x64.zip", "runtime-lock.json"):
            content = name.encode()
            (self.root / name).write_bytes(content)
            lines.append(f"{hashlib.sha256(content).hexdigest()}  {name}\n")
        (self.root / "SHA256SUMS").write_text("".join(lines))
        self.client = FakeGitHub()

    def publish(self):
        publisher.publish(self.client, self.root, "v1.2.3", REVISION)

    def test_uploaded_all_assets_before_publishing_and_repeated_without_changes(self):
        self.publish()
        self.assertFalse(self.client.release["draft"])
        methods = [call[0] for call in self.client.calls]
        self.assertEqual(5, methods.count("UPLOAD"))
        self.assertEqual("PATCH", methods[-1])
        self.client.calls.clear()
        self.publish()
        self.assertTrue(all(call[0] == "GET" for call in self.client.calls))

    def test_rejected_modified_artifact_before_any_api_call(self):
        (self.root / "jftn-1.2.3-linux-x64.zip").write_bytes(b"changed")
        with self.assertRaises(ValueError):
            self.publish()
        self.assertEqual([], self.client.calls)

    def test_rejected_a_tag_pointing_to_another_commit_without_writing(self):
        self.client.tag_revision = "b" * 40
        with self.assertRaises(ValueError):
            self.publish()
        self.assertTrue(all(call[0] == "GET" for call in self.client.calls))

    def test_resumed_an_own_draft_without_overwriting_verified_assets(self):
        self.client.release = {"id": 1, "draft": True, "body": f"JFtn CI revision: {REVISION}",
                               "html_url": "https://github.com/test/repo/releases/tag/v1.2.3"}
        self.client.upload(1, self.root / "jftn-1.2.3-no-jre.zip")
        self.client.calls.clear()
        self.publish()
        self.assertEqual(4, sum(call[0] == "UPLOAD" for call in self.client.calls))

    def test_did_not_replace_conflicting_published_assets(self):
        self.publish()
        self.client.assets["jftn-1.2.3-no-jre.zip"]["digest"] = "sha256:" + "0" * 64
        self.client.calls.clear()
        with self.assertRaises(ValueError):
            self.publish()
        self.assertTrue(all(call[0] == "GET" for call in self.client.calls))

    def test_rejected_traversal_and_untagged_publication(self):
        with self.assertRaises(ValueError):
            publisher.publish(self.client, self.root, "main", REVISION)
        (self.root / "SHA256SUMS").write_text("0" * 64 + "  ../outside.zip\n")
        with self.assertRaises(ValueError):
            self.publish()
        self.assertEqual([], self.client.calls)

    def test_failed_upload_left_a_draft_and_retry_completed_it(self):
        upload = self.client.upload
        def interrupted(release_id, path):
            if path.name.endswith("linux-x64.zip"):
                raise RuntimeError("simulated upload failure")
            return upload(release_id, path)
        self.client.upload = interrupted
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assertTrue(self.client.release["draft"])
        self.assertFalse(any(call[0] == "PATCH" for call in self.client.calls))
        self.client.upload = upload
        self.publish()
        self.assertFalse(self.client.release["draft"])


if __name__ == "__main__":
    unittest.main()
