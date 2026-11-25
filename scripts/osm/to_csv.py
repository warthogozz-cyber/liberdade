"""Reduce an osmium GeoJSON sequence of markets to the published CSV and manifest.

Usage: to_csv.py <input.geojsonseq> <output.csv.gz> <manifest.json> <osm_timestamp>

Standard library only. The output format is decision D15 of plan 10.1.
"""
import csv
import gzip
import hashlib
import io
import json
import sys

HEADER = [
    "osm_type", "osm_id", "kind", "name", "brand", "lat", "lon", "website", "instagram",
    "opening_hours", "street", "housenumber", "suburb", "city",
]

ATTRIBUTION = "© OpenStreetMap contributors"
LICENSE = "ODbL-1.0"

SHOP_KINDS = {
    "supermarket": "SUPERMARKET",
    "convenience": "CONVENIENCE",
    "greengrocer": "GREENGROCER",
}


def kind_of(tags):
    if tags.get("amenity") == "marketplace":
        return "MARKETPLACE"
    return SHOP_KINDS.get(tags.get("shop"))


def identity_of(feature_id):
    """Map an osmium `--add-unique-id=type_id` id to (osm_type, osm_id).

    Prefix n is a node and w is a way kept as a line. Prefix a is an area: osmium numbers it
    2 * way id for a way and 2 * relation id + 1 for a relation.
    """
    if not isinstance(feature_id, str) or len(feature_id) < 2 or not feature_id[1:].isdigit():
        return None
    prefix, number = feature_id[0], int(feature_id[1:])
    if prefix == "n":
        return "n", number
    if prefix == "w":
        return "w", number
    if prefix == "a":
        return ("r", number // 2) if number % 2 else ("w", number // 2)
    return None


def positions(coordinates):
    """Yield every (lon, lat) pair of a GeoJSON coordinates value of any nesting depth."""
    if len(coordinates) >= 2 and isinstance(coordinates[0], (int, float)):
        yield coordinates[0], coordinates[1]
        return
    for part in coordinates:
        yield from positions(part)


def center_of(geometry):
    if not geometry or not geometry.get("coordinates"):
        return None
    points = list(positions(geometry["coordinates"]))
    if not points:
        return None
    lons = [lon for lon, _ in points]
    lats = [lat for _, lat in points]
    return (min(lats) + max(lats)) / 2, (min(lons) + max(lons)) / 2


def decimal(value):
    text = format(value, ".5f")
    return text[1:] if text == "-0.00000" else text


def to_row(feature):
    tags = feature.get("properties") or {}
    kind = kind_of(tags)
    identity = identity_of(feature.get("id"))
    center = center_of(feature.get("geometry"))
    if kind is None or identity is None or center is None:
        return None
    lat, lon = center
    return [
        identity[0], str(identity[1]), kind,
        tags.get("name", ""), tags.get("brand", ""),
        decimal(lat), decimal(lon),
        tags.get("website") or tags.get("contact:website") or "",
        tags.get("contact:instagram", ""),
        tags.get("opening_hours", ""),
        tags.get("addr:street", ""), tags.get("addr:housenumber", ""),
        tags.get("addr:suburb", ""), tags.get("addr:city", ""),
    ]


def write_rows(rows, handle):
    writer = csv.writer(handle, lineterminator="\n")
    writer.writerow(HEADER)
    writer.writerows(rows)


def read_rows(path):
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip().lstrip("\x1e")  # RFC 8142 may prefix each record with a record separator
            if not line:
                continue
            row = to_row(json.loads(line))
            if row is not None:
                yield row


def convert(source, output, manifest_path, osm_timestamp):
    rows = list(read_rows(source))
    with open(output, "wb") as raw:
        # mtime=0 keeps the archive reproducible
        with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as zipped:
            text = io.TextIOWrapper(zipped, encoding="utf-8", newline="", write_through=True)
            write_rows(rows, text)
            text.detach()
    with open(output, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()
    manifest = {
        "osmTimestamp": osm_timestamp,
        "rowCount": len(rows),
        "sha256": digest,
        "attribution": ATTRIBUTION,
        "license": LICENSE,
    }
    with open(manifest_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False)
        handle.write("\n")


def main(argv):
    if len(argv) != 5:
        print(__doc__, file=sys.stderr)
        return 2
    convert(argv[1], argv[2], argv[3], argv[4])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
