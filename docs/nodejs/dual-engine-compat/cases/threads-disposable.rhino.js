const box = threads.disposable();

threads.start(function () {
  box.setAndNotify(files.read("./value.txt"));
});

log(box.blockedGet(5000));
