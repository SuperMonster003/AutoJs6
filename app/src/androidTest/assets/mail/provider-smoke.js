// Real-provider smoke script for `mail.connect` (mail roadmap P3.1). The test prepends
// `var MAIL_SMOKE = { provider, address, password };` from instrumentation arguments and reads
// the report from runtime property `mail.smoke.report`; the report never carries the secret.

var report = { ok: false };
try {
    var connectStarted = Date.now();
    var client = mail.connect({ provider: MAIL_SMOKE.provider, address: MAIL_SMOKE.address, password: MAIL_SMOKE.password });
    report.connectMs = Date.now() - connectStarted;
    var testStarted = Date.now();
    var result = client.test();
    report.testMs = Date.now() - testStarted;
    report.resultOk = result.ok === true;
    report.imapOk = !!(result.imap && result.imap.ok);
    report.smtpOk = !!(result.smtp && result.smtp.ok);
    report.leak = JSON.stringify(result).indexOf(MAIL_SMOKE.password) !== -1;
    client.close();
    report.ok = report.resultOk && !report.leak;
} catch (e) {
    report.error = String(e).split(MAIL_SMOKE.password).join('****');
}
runtime.putProperty('mail.smoke.report', JSON.stringify(report));
