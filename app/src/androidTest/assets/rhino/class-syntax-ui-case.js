// The ui mode case of RhinoClassSyntaxDeviceTest: a class that extends a View class has the extensions of the view
// wrapper of the host (attr, on, and child views by id) along with its own methods and the Java members. The runner adds
// the "ui" header in front and calls rhinoClassUiCase with a function that takes PASS or FAIL.
// zh-CN: RhinoClassSyntaxDeviceTest 的 ui 模式用例: 继承 View 类的 class 同时有自己的方法, Java 成员与宿主 View 包装的扩展 (attr, on,
// 按 id 取子视图). 运行方在开头加 "ui" 头, 并以接收 PASS 或 FAIL 的函数调用 rhinoClassUiCase.

function rhinoClassUiCase(finish) {
    class Badge extends android.widget.TextView {
        draws = 0;
        onDraw(canvas) {
            super.onDraw(canvas);
            this.draws++;
        }
        shout() { return String(this.getText()).toUpperCase(); }
    }
    class Box extends android.widget.FrameLayout {}

    ui.layout(<vertical id="holder"/>);
    var badge = new Badge(activity), box = new Box(activity), clicks = [];
    ui.holder.addView(badge);
    ui.holder.addView(box);
    ui.inflate(<text id="label" text="child"/>, box, true);
    badge.attr('text', 'hello');
    badge.on('click', function (view) { clicks.push(view === badge); }).on('click', function () { clicks.push('chained'); });
    badge.click();
    badge.setId(4242);

    var deadline = Date.now() + 20000;
    function report() {
        var actual = [
            String(badge.getText()), String(badge.attr('text')), badge.shout(), clicks.join('+'), badge.draws > 0,
            badge instanceof Badge, badge instanceof android.widget.TextView, badge instanceof android.view.View,
            ui.holder.getChildAt(0) === badge, ui.holder.findViewById(4242) === badge,
            String(box.label.getText()), typeof badge.attr, typeof badge.on, typeof badge.setText, 'attr' in badge, 'nothing' in badge,
        ].join('|');
        var expected = 'hello|hello|HELLO|true+chained|true|true|true|true|true|true|child|function|function|function|true|false';
        finish(actual === expected ? 'PASS' : 'FAIL expected <' + expected + '> got <' + actual + '>');
    }
    function poll() {
        if (badge.draws > 0 || Date.now() > deadline) {
            try {
                report();
            } catch (e) {
                finish('FAIL threw ' + e);
            }
        } else {
            setTimeout(poll, 50);
        }
    }
    setTimeout(poll, 50);
}
