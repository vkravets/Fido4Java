import binascii
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location(
    "directory_crc", Path(__file__).resolve().parents[1] / "directory_crc.py")
directory_crc = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(directory_crc)


class DirectoryCrcTest(unittest.TestCase):
    def test_known_check_vectors_identify_distinct_algorithms(self):
        self.assertEqual(0xBB3D, directory_crc.crc16_arc(b"123456789"))
        self.assertEqual(0, directory_crc.crc16_arc(b""))
        self.assertEqual(0x31C3, binascii.crc_hqx(b"123456789", 0))

    def test_arc_publication_is_diagnosed_without_treating_it_as_xmodem(self):
        body = b";S Created by example\nBoss,1:500/100\n,1,Station,City,Sysop,-Unpublished-,300\n"
        expected = directory_crc.crc16_arc(body)
        data = b";A Pointlist : %d\n" % expected + body
        result = directory_crc.inspect(data)
        self.assertEqual(["arc_raw"], result["matches"])
        self.assertEqual(expected, result["header_crc"])
        self.assertEqual(0, result["crlf_count"])
        # A real body edit fails even the observed publisher algorithm.
        self.assertEqual([], directory_crc.inspect(data.replace(b"Sysop", b"Other"))["matches"])

    def test_canonical_crlf_is_separate_from_raw_bytes_and_dos_eof(self):
        body = b"Boss,1:500/100\r\n,1,Station,City,Sysop,-Unpublished-,300\r\n"
        expected = binascii.crc_hqx(body, 0)
        original = b";A Pointlist : %d\r\n" % expected + body
        result = directory_crc.inspect(original + b"\x1aignored")
        self.assertIn("xmodem_raw", result["matches"])
        self.assertIn("xmodem_crlf", result["matches"])
        lf = directory_crc.inspect(original.replace(b"\r\n", b"\n"))
        self.assertEqual(["xmodem_crlf"], lf["matches"])
        self.assertNotEqual(result["sha256"], lf["sha256"])

    def test_rejected_missing_or_invalid_headers(self):
        for data in (b"", b";A no terminator", b";A no crc\nbody\n",
                     b";A : 65536\nbody\n", b"Boss,1:500/100\nbody\n"):
            with self.subTest(data=data), self.assertRaises(ValueError):
                directory_crc.inspect(data)

    def test_cli_inspection_never_rewrites_originals(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "points.001"
            original = b";A Pointlist : 47933\n123456789"
            path.write_bytes(original)
            with contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, directory_crc.main([str(path), str(path)]))
            result = json.loads(output.getvalue())
            self.assertEqual(1, len(result))
            self.assertEqual(["arc_raw"], result[0]["matches"])
            self.assertEqual(original, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
