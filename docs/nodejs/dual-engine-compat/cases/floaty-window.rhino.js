const windowHandle = floaty.window({
  text: "S11-28 legacy floaty window",
});
const rawWindowHandle = floaty.rawWindow({
  text: "P12-11 legacy raw floaty window boundary",
});

windowHandle.close();
rawWindowHandle.close();
