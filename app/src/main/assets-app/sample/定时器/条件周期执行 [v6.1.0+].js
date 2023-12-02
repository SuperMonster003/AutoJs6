let config = {
    greeting: 'Good luck comes later...',
    interval: 100,
    listener() {
        console.log(config.greeting);
    },
    condition() {
        let num = Math.random() * 100 + 1;
        return num >= 97 && Math.floor(num);
    },
    callback(res) {
        toastLog(`Your lucky number is ${res}`, 's', 'f');
    },
};

toast(config.greeting, 'l', 'f');


// @Example timers.setIntervalExt()

timers.setIntervalExt(config.listener, config.interval, config.condition, config.callback);


// @Example setInterval()
// @Hint Method setInterval() may be not reliable for time intensive operations.
// @See https://dev.to/akanksha_9560/why-not-to-use-setinterval--2na9

// let intervalId = setInterval(() => {
//     let result;
//     config.listener();
//     if ((result = config.condition())) {
//         clearInterval(intervalId);
//         config.callback(result);
//     }
// }, config.interval);


// @Example do/while loop and sleep()
// @Hint Method sleep() is not available in ui thread and will block current thread.

// let result;
//
// do {
//     sleep(config.interval);
//     config.listener();
// } while (!(result = config.condition()));
//
// config.callback(result);