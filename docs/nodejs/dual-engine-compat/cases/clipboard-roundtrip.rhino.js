const value = "S11-28 clipboard roundtrip";

setClip(value);

if (getClip() !== value) {
  throw new Error("clipboard roundtrip failed");
}

toastLog("clipboard roundtrip ok");
