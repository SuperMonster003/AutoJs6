import os
from pathlib import Path
import re
import shutil
import tempfile
import unittest
import uuid

from dataset.task_io import inherit_parent_acl


def dacl_entries(path):
    """(control flags, ACE strings) of the Windows DACL of path, e.g. ("AI", ["A;ID;FA;;;SY", ...])."""
    import ctypes
    from ctypes import wintypes
    advapi32 = ctypes.WinDLL("advapi32", use_last_error=True)
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    get = advapi32.GetNamedSecurityInfoW
    get.argtypes = (wintypes.LPCWSTR, ctypes.c_int, wintypes.DWORD) + (ctypes.c_void_p,) * 4 + (ctypes.POINTER(ctypes.c_void_p),)
    get.restype = wintypes.DWORD
    convert = advapi32.ConvertSecurityDescriptorToStringSecurityDescriptorW
    convert.argtypes = (ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, ctypes.POINTER(wintypes.LPWSTR), ctypes.c_void_p)
    convert.restype = wintypes.BOOL
    kernel32.LocalFree.argtypes = (ctypes.c_void_p,)
    descriptor, text = ctypes.c_void_p(), wintypes.LPWSTR()
    error = get(str(path), 1, 4, None, None, None, None, ctypes.byref(descriptor))  # SE_FILE_OBJECT, DACL only
    if error:
        raise OSError(None, ctypes.FormatError(error), str(path), error)
    try:
        if not convert(descriptor, 1, 4, ctypes.byref(text), None):  # SDDL_REVISION_1, DACL only
            raise ctypes.WinError(ctypes.get_last_error())
        flags, entries = re.fullmatch(r"D:([A-Z]*)((?:\([^()]*\))*)", text.value).groups()
    finally:
        kernel32.LocalFree(ctypes.cast(text, ctypes.c_void_p))
        kernel32.LocalFree(descriptor)
    return flags, re.findall(r"\(([^()]*)\)", entries)


def mkdtemp_is_restricted():
    """Whether tempfile.mkdtemp() gives a new directory a protected DACL (Windows since Python 3.12.4 and 3.13)."""
    if os.name != "nt":
        return False
    path = tempfile.mkdtemp()
    try:
        return "P" in dacl_entries(path)[0]
    finally:
        os.rmdir(path)


@unittest.skipUnless(mkdtemp_is_restricted(), "tempfile.mkdtemp() does not restrict new directories here")
class InheritParentAclTest(unittest.TestCase):
    """The run directories of train/, construction/ and package/ are staged with mkdtemp() beside their output."""

    def setUp(self):
        # A plain mkdir under the temporary root inherits like an output directory in a checkout does.
        self.parent = Path(tempfile.gettempdir()) / ("autojs6-codegen-acl-" + uuid.uuid4().hex)
        self.parent.mkdir()
        self.addCleanup(shutil.rmtree, self.parent)
        self.reference = self.parent / "plain"
        self.reference.mkdir()
        (self.reference / "plain.json").write_bytes(b"")
        if {entry.split(";")[5] for entry in dacl_entries(self.reference)[1]} <= {"SY", "BA", "OW"}:
            self.skipTest("the temporary root grants nothing beyond SYSTEM, Administrators and the owner")

    def assert_plain(self, path, reference):
        self.assertNotIn("P", dacl_entries(path)[0], "the DACL is still protected from inheritance")
        self.assertEqual(dacl_entries(reference)[1], dacl_entries(path)[1], path.name)

    def test_staging_directory_renamed_into_place(self):
        # train/train.py and package/export_parity.py
        staging = Path(tempfile.mkdtemp(prefix=".run-", dir=self.parent))
        inherit_parent_acl(staging)
        (staging / "model.json").write_bytes(b"{}")
        output = staging.rename(self.parent / "run")
        self.assert_plain(output, self.reference)
        self.assert_plain(output / "model.json", self.reference / "plain.json")

    def test_files_moved_out_of_a_staging_directory(self):
        # construction/train.py and construction/export_ranker.py; the moved file is reset, the staging directory is not.
        staging = Path(tempfile.mkdtemp(prefix=".run-", dir=self.parent))
        (staging / "policy.json").write_bytes(b"{}")
        output = self.parent / "run"
        output.mkdir()
        (staging / "policy.json").rename(output / "policy.json")
        inherit_parent_acl(output / "policy.json")
        self.assert_plain(output / "policy.json", self.reference / "plain.json")


if __name__ == "__main__":
    unittest.main()
