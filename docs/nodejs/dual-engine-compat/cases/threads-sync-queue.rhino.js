let count = 0;
const inc = sync(function () {
  count += 1;
  return count;
});

const first = threads.start(inc);
const second = threads.start(inc);

first.join(1000);
second.join(1000);
