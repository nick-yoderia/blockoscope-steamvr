"""
Builds the Modrinth modpack (.mrpack) for this mod from a Prism Launcher instance.

Third-party mods, shader packs and resource packs are not bundled: each is looked up on Modrinth by its hash and
listed with its download link, and the build fails if one isn't on Modrinth. Only this mod's jar and an allowlist of
config files go into the pack itself, and those are checked for personal information (user names, home paths,
e-mail addresses). Worlds, logs, options.txt, account caches and the like are never read.

    python modpack/make-pack.py --instance <Prism instance dir> --jar build/<mod>.jar --name "Blockoscope X" \
        --out modpack/Blockoscope-X.mrpack
"""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.request
import zipfile

# Config files copied into the pack (the tested settings of Sodium, Iris, Voxy and friends). Anything else in the
# instance's config folder stays out, including this mod's own settings, so its defaults apply.
CONFIGS = [
    "badoptimizations.txt",
    "ferritecore.mixin.properties",
    "iris-excluded.json",
    "iris.properties",
    "lithium.properties",
    "moreculling.toml",
    "sodium-extra-options.json",
    "sodium-extra.properties",
    "sodium-mixins.properties",
    "sodium-options.json",
    "voxy-config.json",
    "voxyworldgenv2.json",
]
# Folders whose files are looked up on Modrinth.
CONTENT = ["mods", "shaderpacks", "resourcepacks"]
CONTENT_EXTENSIONS = (".jar", ".zip")
# Java's Properties timestamp comment ("#Fri Oct 02 22:18:21 PDT 2026") gives away the time zone.
TIMESTAMP_COMMENT = re.compile(r"^#\w{3} \w{3} \d{2} \d{2}:\d{2}:\d{2} \S+ \d{4}\r?\n", re.MULTILINE)
FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def personal_patterns():
    home = os.path.expanduser("~")
    words = {os.path.basename(home), os.environ.get("USERNAME", ""), os.environ.get("USER", "")}
    patterns = [re.escape(w) for w in words if len(w) >= 3]
    patterns += [re.escape(home), re.escape(home.replace("\\", "/")), r"[\w.+-]+@[\w-]+\.[\w.]+", r"[A-Za-z]:[\\/]Users[\\/]"]
    return re.compile("|".join(patterns), re.IGNORECASE)


def modrinth_lookup(hashes):
    request = urllib.request.Request(
        "https://api.modrinth.com/v2/version_files",
        data=json.dumps({"hashes": hashes, "algorithm": "sha1"}).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "blockoscope-modpack-builder"})
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--instance", required=True)
    parser.add_argument("--jar", required=True, help="this mod's jar, bundled in the pack")
    parser.add_argument("--name", required=True)
    parser.add_argument("--summary", default="")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    instance = args.instance
    game = os.path.join(instance, "minecraft")
    with open(os.path.join(instance, "mmc-pack.json"), encoding="utf-8") as f:
        components = {c["uid"]: c["version"] for c in json.load(f)["components"]}
    dependencies = {"minecraft": components["net.minecraft"], "fabric-loader": components["net.fabricmc.fabric-loader"]}

    jar_name = os.path.basename(args.jar)
    mod_prefix = jar_name.rsplit("-", 2)[0] + "-"  # e.g. "blockoscope-steamvr-"
    version = jar_name[len(mod_prefix):-len(".jar")]

    # Third-party content, by hash.
    local = {}
    for folder in CONTENT:
        directory = os.path.join(game, folder)
        if not os.path.isdir(directory):
            continue
        for name in sorted(os.listdir(directory)):
            if not name.endswith(CONTENT_EXTENSIONS) or name.startswith(mod_prefix):
                continue
            with open(os.path.join(directory, name), "rb") as f:
                data = f.read()
            local[hashlib.sha1(data).hexdigest()] = (f"{folder}/{name}", data)
    found = modrinth_lookup(list(local))
    files, missing = [], []
    for sha1, (path, data) in local.items():
        version_info = found.get(sha1)
        file_info = version_info and next(f for f in version_info["files"] if f["hashes"]["sha1"] == sha1)
        if not file_info:
            missing.append(path)
            continue
        files.append({
            "path": path,
            "hashes": {"sha1": sha1, "sha512": hashlib.sha512(data).hexdigest()},
            "downloads": [file_info["url"]],
            "fileSize": len(data),
        })
    if missing:
        sys.exit("Not on Modrinth (won't be bundled): " + ", ".join(missing))
    files.sort(key=lambda f: f["path"])

    # Bundled files, checked for personal information.
    personal = personal_patterns()
    overrides = []
    for name in CONFIGS:
        path = os.path.join(game, "config", name)
        if not os.path.isfile(path):
            continue
        with open(path, encoding="utf-8") as f:
            text = TIMESTAMP_COMMENT.sub("", f.read())
        match = personal.search(text)
        if match:
            sys.exit(f"config/{name} contains personal information ({match.group(0)!r}); not packing")
        overrides.append((f"overrides/config/{name}", text.encode("utf-8")))
    with open(args.jar, "rb") as f:
        overrides.append((f"overrides/mods/{jar_name}", f.read()))

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": args.name,
        "summary": args.summary,
        "files": files,
        "dependencies": dependencies,
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as pack:
        def write(path, data):
            pack.writestr(zipfile.ZipInfo(path, FIXED_TIME), data, zipfile.ZIP_DEFLATED)
        write("modrinth.index.json", json.dumps(index, indent=2) + "\n")
        for path, data in overrides:
            write(path, data)

    print(f"{args.out}: {args.name} {version} for Minecraft {dependencies['minecraft']} "
          f"(Fabric Loader {dependencies['fabric-loader']})")
    for f in files:
        print(f"  {f['path']}  <- {f['downloads'][0]}")
    for path, _ in overrides:
        print(f"  {path}  (bundled)")


if __name__ == "__main__":
    main()
