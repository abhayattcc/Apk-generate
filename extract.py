import os
import sys
import zipfile
import shutil

ZIP_NAME = "SlitherArena.zip"
DEST = "."
DELETE_ZIP_AFTER = True  # remove the zip after a successful extract

SKIP_PREFIXES = ("__MACOSX/", ".git/", ".github/")


def main():
    if not os.path.exists(ZIP_NAME):
        print(f"{ZIP_NAME} not found, nothing to do.")
        return 0

    with zipfile.ZipFile(ZIP_NAME) as zf:
        names = [
            n for n in zf.namelist()
            if not n.startswith(SKIP_PREFIXES) and not n.endswith(".DS_Store")
        ]

        # If everything sits inside one top-level folder, strip it
        # so files land directly in the repo root.
        tops = {n.split("/")[0] for n in names if n.strip("/")}
        strip = None
        if len(tops) == 1:
            only = next(iter(tops))
            if any(n.startswith(only + "/") for n in names):
                strip = only + "/"

        dest_root = os.path.abspath(DEST)
        count = 0

        for info in zf.infolist():
            name = info.filename
            if name not in names:
                continue
            rel = name[len(strip):] if strip and name.startswith(strip) else name
            if not rel:
                continue

            target = os.path.abspath(os.path.join(DEST, rel))
            # zip-slip protection
            if not (target == dest_root or target.startswith(dest_root + os.sep)):
                print(f"Skipping unsafe path: {name}")
                continue

            if info.is_dir():
                os.makedirs(target, exist_ok=True)
                continue

            os.makedirs(os.path.dirname(target), exist_ok=True)
            with zf.open(info) as src, open(target, "wb") as out:
                shutil.copyfileobj(src, out)
            count += 1

    print(f"Extracted {count} files to repo root.")

    if DELETE_ZIP_AFTER:
        os.remove(ZIP_NAME)
        print(f"Deleted {ZIP_NAME}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())