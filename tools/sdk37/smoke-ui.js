"ui";

// Run from AutoJs6 after granting overlay access. Accept the screen-capture
// dialog for this test's own activity. No screenshot is saved to storage.
// This covers the script UI / overlay / MediaProjection paths; the Symbols
// Settings keyboard change still needs its separate activity-level check.

ui.layout(
    <vertical padding="24" bg="#ffffff">
        <text text="SDK 37 migration smoke" textSize="22sp" textColor="#000000"/>
        <input id="editor" text="SDK 37 input" textColor="#000000"/>
        <text text="Accept the screen-capture dialog for this test." textColor="#000000"/>
    </vertical>
);

let overlay = null;
let finished = false;
const timeout = threads.start(() => {
    sleep(60000);
    if (!finished) {
        console.error("[SDK37] FAIL: screen-capture test timed out");
        exit();
    }
});

events.on("exit", () => {
    finished = true;
    timeout.interrupt();
    if (overlay) overlay.close();
});

threads.start(() => {
    try {
        sleep(1000);
        ui.run(() => {
            ui.editor.requestFocus();
            const imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(ui.editor, 0);
            if (!ui.editor.hasFocus()) throw new Error("Script editor has no focus");
        });
        console.log("[SDK37] PASS: script Activity and editable view");

        overlay = floaty.rawWindow(
            <frame bg="#e3f2fd" padding="12">
                <text text="SDK 37 overlay" textColor="#000000"/>
            </frame>
        );
        ui.run(() => {
            overlay.setPosition(30, 350);
            overlay.setSize(300, 100);
            overlay.setTouchable(false);
        });
        sleep(1000);
        if (overlay.getWidth() <= 0 || overlay.getHeight() <= 0) {
            throw new Error("Overlay has no measured bounds");
        }
        console.log("[SDK37] PASS: overlay window");

        console.log("[SDK37] WAIT: accept the system screen-capture dialog");
        if (!requestScreenCapture()) throw new Error("Screen capture was not granted");
        const captured = captureScreen();
        if (!captured || captured.getWidth() <= 0 || captured.getHeight() <= 0) {
            throw new Error("Screen capture returned no pixels");
        }
        console.log(`[SDK37] PASS: MediaProjection ${captured.getWidth()}x${captured.getHeight()}`);
        console.log("[SDK37] PASS: all UI smoke checks");
    } catch (error) {
        console.error(`[SDK37] FAIL: ${error}`);
    } finally {
        finished = true;
        timeout.interrupt();
        if (overlay) {
            overlay.close();
            overlay = null;
        }
        ui.finish();
    }
});
