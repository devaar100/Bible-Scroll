from pathlib import Path

PIPELINE = Path(__file__).resolve().parents[2]
REPO = PIPELINE.parent
DATA = PIPELINE / "data"
RAW = DATA / "raw"
OUT = PIPELINE / "out"
SOURCE_DB = DATA / "source.db"
MANIFEST = RAW / "manifest.json"
# The app's bundled Bible; its KJV verse list is the target versification.
APP_BIBLE_DB = REPO / "app/src/main/assets/bible.db"
