import csv
import gzip
import hashlib
import io
import json
import os
import tempfile
import unittest

import to_csv

HERE = os.path.dirname(os.path.abspath(__file__))
SAMPLE = os.path.join(HERE, "testdata", "sample.geojsonseq")
EXPECTED = os.path.join(HERE, "testdata", "expected.csv")
TIMESTAMP = "2026-11-23T21:21:00Z"


def run_sample(directory):
    out = os.path.join(directory, "markets-br.csv.gz")
    manifest = os.path.join(directory, "manifest.json")
    to_csv.convert(SAMPLE, out, manifest, TIMESTAMP)
    return out, manifest


def square(lon_a, lat_a, lon_b, lat_b):
    return [[[lon_a, lat_a], [lon_b, lat_a], [lon_b, lat_b], [lon_a, lat_b], [lon_a, lat_a]]]


class ToCsvTest(unittest.TestCase):
    def test_sample_matches_expected(self):
        with tempfile.TemporaryDirectory() as directory:
            out, _ = run_sample(directory)
            with gzip.open(out, "rb") as handle:
                produced = handle.read()
        with open(EXPECTED, "rb") as handle:
            self.assertEqual(handle.read(), produced)

    def test_polygon_uses_bbox_center(self):
        feature = {
            "type": "Feature",
            "id": "a2002",
            "properties": {"shop": "greengrocer"},
            "geometry": {"type": "Polygon", "coordinates": square(-50.0, -10.0, -50.002, -10.002)},
        }
        row = to_csv.to_row(feature)
        self.assertEqual(["-10.00100", "-50.00100"], row[5:7])

    def test_unknown_kind_is_dropped(self):
        feature = {
            "type": "Feature",
            "id": "n1005",
            "properties": {"shop": "bakery"},
            "geometry": {"type": "Point", "coordinates": [-50.0, -10.0]},
        }
        self.assertIsNone(to_csv.to_row(feature))

    def test_object_without_position_is_dropped(self):
        feature = {"type": "Feature", "id": "n1", "properties": {"shop": "supermarket"}, "geometry": None}
        self.assertIsNone(to_csv.to_row(feature))

    def test_manifest_has_sha_and_count(self):
        with tempfile.TemporaryDirectory() as directory:
            out, manifest_path = run_sample(directory)
            with open(out, "rb") as handle:
                digest = hashlib.sha256(handle.read()).hexdigest()
            with open(manifest_path, encoding="utf-8") as handle:
                manifest = json.load(handle)
        self.assertEqual(digest, manifest["sha256"])
        self.assertEqual(4, manifest["rowCount"])
        self.assertEqual("ODbL-1.0", manifest["license"])
        self.assertEqual(TIMESTAMP, manifest["osmTimestamp"])
        self.assertEqual("© OpenStreetMap contributors", manifest["attribution"])

    def test_quotes_commas_in_names(self):
        feature = {
            "type": "Feature",
            "id": "n1",
            "properties": {"shop": "supermarket", "name": "Mercado Exemplo, Filial"},
            "geometry": {"type": "Point", "coordinates": [-50.0, -10.0]},
        }
        buffer = io.StringIO()
        to_csv.write_rows([to_csv.to_row(feature)], buffer)
        lines = buffer.getvalue().splitlines()
        self.assertIn('"Mercado Exemplo, Filial"', lines[1])
        self.assertEqual("Mercado Exemplo, Filial", next(csv.DictReader(io.StringIO(buffer.getvalue())))["name"])

    def test_osm_identity_from_unique_id(self):
        def row_for(feature_id):
            return to_csv.to_row({
                "type": "Feature",
                "id": feature_id,
                "properties": {"shop": "supermarket"},
                "geometry": {"type": "Point", "coordinates": [-50.0, -10.0]},
            })[:3]

        self.assertEqual(["n", "7", "SUPERMARKET"], row_for("n7"))
        self.assertEqual(["w", "1001", "SUPERMARKET"], row_for("a2002"))
        self.assertEqual(["r", "1502", "SUPERMARKET"], row_for("a3005"))
        self.assertEqual(["w", "9", "SUPERMARKET"], row_for("w9"))


if __name__ == "__main__":
    unittest.main()
