auto();
events.observeKey();
events.setKeyInterceptionEnabled(true);
events.on('volume_down', () => {
    app.sendBroadcast('bounds');
    exit();
});