module.exports = {
    await(promise) {
        const cont = continuation.create();
        promise
            .then(result => cont.resume(result))
            .catch(error => cont.resumeError(error));
        return cont.await();
    },
    delay(millis) {
        const cont = continuation.create();
        setTimeout(() => cont.resume(), millis);
        cont.await();
    },
};