"""Synchronize the canonical core and Android adapter without touching credentials."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

parser = argparse.ArgumentParser()
parser.add_argument("--check", action="store_true")
parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
args = parser.parse_args()
source = Path(__file__).resolve().parents[1]
packages = {"dualsense-tester": "dualsensetester", "xbox-tester": "xboxtester", "joycon-tester": "joycontester"}
files = [p for p in (source / "diagnostics-core").rglob("*")
         if p.is_file() and "build" not in p.relative_to(source / "diagnostics-core").parts]
failed = []
for repo, package in packages.items():
    root = args.root / repo
    if not (root / "app/build.gradle").is_file():
        raise SystemExit("Not an expected Android tester: " + str(root))
    contents = {str(p.relative_to(source)): p.read_bytes() for p in files}
    adapter = source / "android/BaseTestActivity.java.template"
    if adapter.exists():
        contents["app/src/main/java/com/remteh/" + package + "/BaseTestActivity.java"] = \
            adapter.read_text().replace("__APP_PACKAGE__", package).encode()
    contents["app/src/main/java/com/remteh/support/SupportDialog.java"] = (source / "android/SupportDialog.java").read_bytes()
    contents["app/src/main/assets/remteh-support.json"] = (source / "support/registry.json").read_bytes()
    manifest = {}
    for relative, payload in contents.items():
        destination = root / relative
        if args.check:
            if not destination.is_file() or destination.read_bytes() != payload:
                failed.append(repo + "/" + relative)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(payload)
        manifest[relative] = hashlib.sha256(payload).hexdigest()
    source_record = {
        "component": "RemTeh Device Lab", "schema": 1, "files": manifest,
        "upstream": "https://github.com/remteh-by/Device-Lab; see docs/DEVICE-LAB.md"
    }
    record_path = root / "diagnostics-core/SOURCE.json"
    if args.check:
        try:
            if json.loads(record_path.read_text()) != source_record:
                failed.append(repo + "/diagnostics-core/SOURCE.json")
        except (OSError, ValueError):
            failed.append(repo + "/diagnostics-core/SOURCE.json")
    else:
        record_path.write_text(json.dumps(source_record, indent=2) + "\n")
if failed:
    raise SystemExit("Shared source differs:\n" + "\n".join(failed))
print("Three tester copies match" if args.check else "Synchronized three tester copies")
