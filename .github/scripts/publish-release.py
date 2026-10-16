"""Publish verified CI artifacts as a draft first, then expose the complete release."""

import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen


class GitHub:
    def __init__(self, repository, token):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
            raise ValueError("Invalid GitHub repository")
        self.repository = repository
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "jftn-github-release",
        }

    def request(self, method, path, payload=None):
        body = None if payload is None else json.dumps(payload).encode()
        headers = {**self.headers, "Content-Type": "application/json"}
        request = Request(f"https://api.github.com/repos/{self.repository}/{path}",
                          data=body, headers=headers, method=method)
        try:
            with urlopen(request, timeout=120) as response:
                return json.load(response)
        except HTTPError as error:
            if error.code == 404 and method == "GET":
                return None
            raise RuntimeError(f"GitHub {method} {path} returned HTTP {error.code}") from None

    def upload(self, release_id, path):
        endpoint = f"https://uploads.github.com/repos/{self.repository}/releases/{release_id}/assets"
        headers = {**self.headers, "Content-Type": "application/octet-stream",
                   "Content-Length": str(path.stat().st_size)}
        # http.client streams this file; do not hold runtime ZIPs in memory.
        with path.open("rb") as content:
            request = Request(endpoint + "?" + urlencode({"name": path.name}),
                              data=content, headers=headers, method="POST")
            try:
                with urlopen(request, timeout=300) as response:
                    return json.load(response)
            except HTTPError as error:
                raise RuntimeError(f"Upload {path.name} returned HTTP {error.code}") from None


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def artifacts(directory):
    manifest = directory / "SHA256SUMS"
    result = []
    for line in manifest.read_text().splitlines():
        expected, name = line.split(maxsplit=1)
        name = name.removeprefix("*").removeprefix("./")
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", name) or not re.fullmatch(r"[a-f0-9]{64}", expected):
            raise ValueError("Invalid checksum manifest")
        path = directory / name
        if path in result or path.is_symlink() or digest(path) != expected:
            raise ValueError(f"Invalid or duplicate release artifact: {name}")
        result.append(path)
    zips = [path.name for path in result if path.suffix == ".zip"]
    if len(zips) != 3 or not all(any(name.endswith(suffix) for name in zips)
                              for suffix in ("-no-jre.zip", "-linux-x64.zip", "-windows-x64.zip")):
        raise ValueError("Expected portable, Linux x64 and Windows x64 ZIPs")
    if {path.name for path in result} != {*zips, "runtime-lock.json"}:
        raise ValueError("Unexpected release assets")
    return [*result, manifest]


def tag_commit(client, tag):
    reference = client.request("GET", "git/ref/tags/" + quote(tag, safe=""))
    if reference is None:
        raise ValueError("Release tag does not exist on GitHub")
    target = reference["object"]
    for _ in range(8):
        if target["type"] == "commit":
            return target["sha"]
        if target["type"] != "tag":
            break
        target = client.request("GET", "git/tags/" + target["sha"])["object"]
    raise ValueError("Release tag does not resolve to a commit")


def ensure_assets(client, release, paths):
    existing = {asset["name"]: asset for asset in client.request("GET", f"releases/{release['id']}/assets?per_page=100")}
    for path in paths:
        expected = "sha256:" + digest(path)
        old = existing.get(path.name)
        if old is not None:
            if old.get("digest") != expected or old.get("state") != "uploaded":
                raise ValueError(f"Existing asset differs: {path.name}; no assets were overwritten")
            continue
        if not release["draft"]:
            raise ValueError("Published release is incomplete; refusing to modify it")
        uploaded = client.upload(release["id"], path)
        if uploaded.get("digest") != expected or uploaded.get("state") != "uploaded":
            raise ValueError(f"GitHub did not confirm the checksum of {path.name}")


def publish(client, directory, tag, revision):
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", tag):
        raise ValueError("Releases require a vMAJOR.MINOR.PATCH tag, with optional prerelease suffix")
    paths = artifacts(directory)
    expected_names = {f"jftn-{tag[1:]}{suffix}" for suffix in ("-no-jre.zip", "-linux-x64.zip", "-windows-x64.zip")}
    if {path.name for path in paths if path.suffix == ".zip"} != expected_names:
        raise ValueError("Artifact versions do not match the release tag")
    if not re.fullmatch(r"[a-f0-9]{40}", revision) or tag_commit(client, tag) != revision:
        raise ValueError("Release tag and compiled artifact revision differ")
    marker = f"JFtn CI revision: {revision}"
    release = client.request("GET", "releases/tags/" + quote(tag, safe=""))
    if release is None:
        release = client.request("POST", "releases", {
            "tag_name": tag, "target_commitish": revision, "name": f"JFtn {tag[1:]}",
            "body": marker, "draft": True, "prerelease": "-" in tag,
            "generate_release_notes": True,
        })
    elif marker not in release.get("body", ""):
        raise ValueError("Existing release was not created from these CI artifacts")
    ensure_assets(client, release, paths)
    if release["draft"]:
        release = client.request("PATCH", f"releases/{release['id']}", {
            "draft": False, "make_latest": "false" if "-" in tag else "legacy"
        })
    print("Release ready:", release["html_url"])


if __name__ == "__main__":
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "build/release")
    token = os.environ.get("GH_TOKEN", "")
    if not token:
        raise SystemExit("Pass the GitHub Actions token as GH_TOKEN with contents: write permission")
    repository = os.environ["GITHUB_REPOSITORY"]
    publish(GitHub(repository, token), root, os.environ["GITHUB_REF_NAME"],
            (root / "revision.txt").read_text().strip())
