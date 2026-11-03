// Probe of tools/screen-capture/device_capture_check.py. The runner prepends "var CHECK = {...};" and reads the
// "AJ6CAP" lines of the console.
(function () {
    var say = function (s) {
        console.log('AJ6CAP ' + s);
    };

    function displaySize() {
        var p = new android.graphics.Point();
        context.getSystemService(android.content.Context.DISPLAY_SERVICE).getDisplay(0).getRealSize(p);
        return p.x + 'x' + p.y;
    }

    function black(img, x, y) {
        var c = images.pixel(img, x, y);
        return colors.red(c) < 12 && colors.green(c) < 12 && colors.blue(c) < 12;
    }

    // Pure black edges around a non-black center: the content is letterboxed in the frame (issue #337).
    function letterbox(img) {
        var w = img.width, h = img.height;
        if (black(img, w >> 1, h >> 1)) return 'unknown';
        var sides = black(img, 2, h >> 1) && black(img, w - 3, h >> 1);
        var ends = black(img, w >> 2, 2) && black(img, w >> 2, h - 3);
        return sides || ends ? 'yes' : 'no';
    }

    function shot(label) {
        var start = Date.now();
        var img = images.captureScreen();
        var ms = Date.now() - start;
        say(label + ' frame=' + img.width + 'x' + img.height + ' display=' + displaySize() + ' ms=' + ms + ' letterbox=' + letterbox(img));
    }

    function describe(e) {
        // The thrown exception itself: its cause may be a RemoteException carrying the remote stack trace.
        var t = e && e.javaException ? e.javaException : e;
        var name = t && t.getClass ? t.getClass().getSimpleName() : 'Error';
        var message = String(t && t.getMessage ? t.getMessage() : e);
        return name + ': ' + message.slice(0, 48);
    }

    try {
        var granted = CHECK.orientation === 'auto'
            ? images.requestScreenCapture()
            : images.requestScreenCapture({orientation: CHECK.orientation});
        say('request ' + granted);
        if (!granted) return;
        for (var i = 0; i < CHECK.statics; i++) {
            shot('static-' + i);
            sleep(150);
        }
        if (CHECK.rotate) {
            say('rotate-ready');
            var until = Date.now() + CHECK.rotateMs;
            for (var j = 0; Date.now() < until; j++) {
                shot('rotating-' + j);
                sleep(200);
            }
        }
        if (CHECK.secondDisplay) {
            // What 6.7.0 did after a capture timed out: another VirtualDisplay on the same projection.
            var projection = runtime.images.getScreenCapturer().getAudioProjection();
            try {
                projection.createVirtualDisplay('AJ6CAP', 320, 240, 160, 0, null, null, null).release();
                say('second-display created');
            } catch (e) {
                say('second-display ' + describe(e));
            }
            sleep(800);
            var capturer = runtime.images.getScreenCapturer();
            say('capturer-after ' + (capturer == null ? 'none' : capturer.isReleased() ? 'released' : 'active'));
            shot('recovered');
        }
    } catch (e) {
        say('error ' + describe(e));
    } finally {
        say('end');
    }
})();
