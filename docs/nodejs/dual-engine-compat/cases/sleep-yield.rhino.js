const startedAt = Date.now();

sleep(25);

if (Date.now() < startedAt) {
  throw new Error("clock moved backwards");
}

toastLog("sleep completed");
