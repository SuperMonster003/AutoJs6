// Device smoke script for the `mail` global (mail roadmap P3.1). The test prepends
// `var MAIL_SMOKE = { port, closedPort, password };` (a loopback fake IMAP server owned by the
// test process) and reads the JSON report from runtime property `mail.smoke.report` once the
// async chain has settled. Every check throws on failure so the report names the first one.

var report = { steps: [], ok: false };

function expect(condition, message) {
    if (!condition) throw new Error('smoke: ' + message);
    report.steps.push(message);
}

function expectMailError(fn, code) {
    var error = null;
    try {
        fn();
    } catch (e) {
        error = e;
    }
    expect(error !== null, 'expected MailError ' + code + ' but nothing was thrown');
    expect(error instanceof mail.MailError, 'expected MailError ' + code + ', got ' + error);
    expect(error instanceof Error, 'MailError ' + code + ' extends Error');
    expect(error.code === code, 'expected ' + code + ', got ' + error.code + ' (' + error.message + ')');
    return error;
}

function expectRejection(promise, code) {
    return promise.then(function () {
        throw new Error('smoke: expected rejection ' + code + ' but the promise resolved');
    }, function (e) {
        expect(e instanceof mail.MailError && e.code === code, 'rejection is MailError ' + code + ', got ' + e);
    });
}

var account = {
    address: 'alice@localhost',
    user: 'alice',
    password: MAIL_SMOKE.password,
    imap: { host: '127.0.0.1', port: MAIL_SMOKE.port, tls: 'none' },
    timeout: { connect: 5000, read: 20000 },
};

// The global object and its metadata parts need no session.
expect(typeof mail === 'object' && typeof mail.connect === 'function', 'mail global is mounted');
expect(typeof mail.MailError === 'function' && new mail.MailError('x', 'CODE').code === 'CODE', 'mail.MailError is a constructor');
expect(mail.default == null, 'no default client before setDefault');
var providers = mail.providers.list();
expect(Array.isArray(providers) && providers.length > 0, 'providers.list() is a non-empty array');
var qq = mail.providers.get('qq');
expect(qq !== null && qq.id === 'qq' && typeof qq.imap === 'object', 'providers.get("qq") has the qq preset');
expect(mail.providers.get('no-such-provider') === null, 'providers.get(unknown) is null');
var resolved = mail.providers.resolve('someone@163.com');
expect(resolved !== null && resolved.id === '163', 'providers.resolve("someone@163.com") is the 163 preset');
expect(mail.providers.resolve('someone@example.test') === null, 'providers.resolve(unknown domain) is null');
var accounts = mail.accounts.list();
expect(Array.isArray(accounts) && accounts.length === 0, 'accounts.list() is empty before roadmap P4');
expect(mail.accounts.has('work') === false, 'accounts.has(alias) is false before roadmap P4');

// Refusals arrive as MailError before or from the plugin.
expectMailError(function () { mail.test(); }, 'NO_DEFAULT_ACCOUNT');
expectMailError(function () { mail.connect(); }, 'INVALID_ARGUMENT');
expectMailError(function () { mail.connect({ address: 'nobody' }); }, 'INVALID_ARGUMENT');
expectMailError(function () { mail.connect({ address: 'a@b.c', password: 'x', bogus: 1 }); }, 'INVALID_ARGUMENT');
expectMailError(function () { mail.connect({ address: 'a@b.c', password: 'x', tokenProvider: 'not a function' }); }, 'INVALID_ARGUMENT');
expectMailError(function () { mail.connect('no-such-alias'); }, 'ACCOUNT_NOT_FOUND');
// connect() opens the plugin session only; the first operation connects to the server, and
// test() reports an unreachable endpoint inside its result instead of throwing.
var unreachable = mail.connect({ address: 'alice@localhost', password: 'refused-secret', imap: { host: '127.0.0.1', port: MAIL_SMOKE.closedPort, tls: 'none' }, timeout: { connect: 3000 } });
expect(unreachable.isConnected === true, 'connect() succeeds without a server: the first operation connects');
var probe = unreachable.test();
expect(probe.ok === false && probe.imap.ok === false, 'test() against a closed port reports ok: false, got ' + JSON.stringify(probe));
expect(probe.imap.error && probe.imap.error.code === 'CONNECT_FAILED', 'the closed port is reported as CONNECT_FAILED, got ' + JSON.stringify(probe.imap.error));
expect(JSON.stringify(probe).indexOf('refused-secret') === -1, 'a failed probe carries no password');
unreachable.close();
expect(unreachable.isClosed === true, 'close() after a failed probe works');

// A client against the fake server.
var connectStarted = Date.now();
var client = mail.connect(account);
report.connectMs = Date.now() - connectStarted;
expect(String(client).indexOf('MailClient') === 0, 'client toString names MailClient');
expect(client.isConnected === true && client.isClosed === false, 'client is connected and not closed');
expect(client.account.address === 'alice@localhost' && client.account.imap.port === MAIL_SMOKE.port, 'client.account mirrors the options');
expect(!('password' in client.account) && Object.keys(client.account).indexOf('password') === -1, 'client.account has no password');
var testStarted = Date.now();
var result = client.test();
report.testMs = Date.now() - testStarted;
expect(result.ok === true && result.imap.ok === true && result.imap.port === MAIL_SMOKE.port, 'client.test() reaches the fake server');
expect(JSON.stringify(result).indexOf(MAIL_SMOKE.password) === -1, 'test result carries no password');
expect(mail.setDefault(client) === client, 'setDefault returns the client');
expect(mail.default === client, 'mail.default is the client');
expect(mail.test().ok === true, 'mail.test() forwards to the default client');
expectMailError(function () { client.test('extra'); }, 'INVALID_ARGUMENT');

// The async forms settle on the script thread.
var asyncStarted = Date.now();
client.testAsync().then(function (r) {
    expect(r.ok === true, 'client.testAsync() resolves ok');
    report.testAsyncMs = Date.now() - asyncStarted;
    return mail.testAsync();
}).then(function (r) {
    expect(r.ok === true, 'mail.testAsync() forwards to the default client');
    var debugAccount = JSON.parse(JSON.stringify(account));
    debugAccount.debug = true;
    return mail.connectAsync(debugAccount);
}).then(function (second) {
    expect(second !== client && second.isConnected === true, 'connectAsync resolves a second connected client');
    expect(second.test().ok === true, 'the debug client answers test()');
    second.close();
    expect(second.isClosed === true && second.isConnected === false, 'close() marks the client closed');
    expectMailError(function () { second.test(); }, 'SESSION_CLOSED');
    expectMailError(function () { mail.setDefault(second); }, 'INVALID_ARGUMENT');
    return expectRejection(second.testAsync(), 'SESSION_CLOSED');
}).then(function () {
    return expectRejection(mail.connectAsync('no-such-alias'), 'ACCOUNT_NOT_FOUND');
}).then(function () {
    return expectRejection(mail.connectAsync({ address: 'nobody' }), 'INVALID_ARGUMENT');
}).then(function () {
    mail.close();
    expect(client.isClosed === true, 'mail.close() closes the default client');
    expect(mail.default == null, 'mail.close() clears the default client');
    expectMailError(function () { mail.test(); }, 'NO_DEFAULT_ACCOUNT');
    return expectRejection(mail.testAsync(), 'NO_DEFAULT_ACCOUNT');
}).then(function () {
    report.ok = true;
}).catch(function (e) {
    report.error = String(e) + (e && e.stack ? '\n' + e.stack : '');
}).then(function () {
    runtime.putProperty('mail.smoke.report', JSON.stringify(report));
});
