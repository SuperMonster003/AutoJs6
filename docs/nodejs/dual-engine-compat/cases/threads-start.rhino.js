const worker = threads.start(function () {
  toastLog("legacy threads.start body");
});

worker.join(1000);
