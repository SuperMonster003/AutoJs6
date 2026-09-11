"""Print ELF program-header layout facts relevant to 16 KB page sizes.

For each PT_LOAD: offset, vaddr, filesz, memsz, flags, align. For PT_GNU_RELRO: start, end,
and whether the end is 16 KB aligned. A RELRO end that is not 16 KB aligned lets the dynamic
linker's mprotect() spill into the writable data that follows it on 16 KB devices, which
crashes with SIGSEGV / SEGV_ACCERR on the first write even when every PT_LOAD is 16 KB aligned.

Usage: python tools/16kb/elf_layout.py <file.so | archive.aar!jni/arm64-v8a/x.so | apk!lib/arm64-v8a/x.so> ...
"""
import struct
import sys
import zipfile
from pathlib import Path

PAGE = 16384
PT_LOAD, PT_DYNAMIC, PT_GNU_RELRO = 1, 2, 0x6474E552


def read_bytes(spec: str) -> bytes:
    if "!" in spec:
        archive, entry = spec.split("!", 1)
        with zipfile.ZipFile(archive) as zf:
            return zf.read(entry)
    return Path(spec).read_bytes()


def program_headers(data: bytes):
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    cls = data[4]
    end = "<" if data[5] == 1 else ">"
    if cls == 2:
        e_phoff = struct.unpack_from(end + "Q", data, 0x20)[0]
        e_phentsize, e_phnum = struct.unpack_from(end + "HH", data, 0x36)
        for i in range(e_phnum):
            off = e_phoff + i * e_phentsize
            p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from(end + "IIQQQQQQ", data, off)
            yield dict(type=p_type, flags=p_flags, offset=p_offset, vaddr=p_vaddr, filesz=p_filesz, memsz=p_memsz, align=p_align)
    else:
        e_phoff = struct.unpack_from(end + "I", data, 0x1C)[0]
        e_phentsize, e_phnum = struct.unpack_from(end + "HH", data, 0x2A)
        for i in range(e_phnum):
            off = e_phoff + i * e_phentsize
            p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from(end + "IIIIIIII", data, off)
            yield dict(type=p_type, flags=p_flags, offset=p_offset, vaddr=p_vaddr, filesz=p_filesz, memsz=p_memsz, align=p_align)


def flags(f: int) -> str:
    return ("R" if f & 4 else "-") + ("W" if f & 2 else "-") + ("X" if f & 1 else "-")


def describe(spec: str) -> bool:
    data = read_bytes(spec)
    phdrs = list(program_headers(data))
    loads = [p for p in phdrs if p["type"] == PT_LOAD]
    relros = [p for p in phdrs if p["type"] == PT_GNU_RELRO]
    print(f"== {spec} ({len(data)} bytes)")
    ok = True
    for p in loads:
        print(f"  LOAD  {flags(p['flags'])} off=0x{p['offset']:x} vaddr=0x{p['vaddr']:x} filesz=0x{p['filesz']:x} memsz=0x{p['memsz']:x} align=0x{p['align']:x}")
        if p["align"] < PAGE:
            ok = False
    for r in relros:
        relro_end = r["vaddr"] + r["memsz"]
        aligned = relro_end % PAGE == 0
        # find the RW LOAD that contains the RELRO region and report what follows the RELRO end
        spill = ""
        for p in loads:
            if p["flags"] & 2 and p["vaddr"] <= r["vaddr"] < p["vaddr"] + p["memsz"]:
                load_end = p["vaddr"] + p["memsz"]
                protected_end = -(-relro_end // PAGE) * PAGE
                if not aligned and load_end > relro_end:
                    spill = f" -> mprotect on 16 KB pages would cover 0x{relro_end:x}..0x{min(protected_end, load_end):x} of writable data (RW LOAD ends 0x{load_end:x})"
        print(f"  RELRO start=0x{r['vaddr']:x} end=0x{relro_end:x} end%16K={'aligned' if aligned else 'NOT aligned'}{spill}")
        if not aligned and spill:
            ok = False
    if not relros:
        print("  (no PT_GNU_RELRO)")
    print(f"  verdict: {'OK' if ok else 'RISK'}")
    return ok


if __name__ == "__main__":
    results = [describe(spec) for spec in sys.argv[1:]]
    sys.exit(0 if all(results) else 1)
