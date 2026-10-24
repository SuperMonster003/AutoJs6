// Cases of the script sqlite module's onDowngrade (docs/db-downgrade/ROADMAP.md P3.1, D6 / T5).
// Run by sqlite_ondowngrade.py through RunIntentActivity, in a directory of the host's own files; it writes
// result.json next to itself (relative paths resolve against the script's working directory). Each case keeps
// what the checks need: versions, row counts, callback events and argument types, the first line of an error.

var result = { cases: {} };
var NAMES = ['p3-plain.db', 'p3-partial.db', 'p3-callback.db', 'p3-throw.db', 'p3-readonly.db'];

function removeDatabases() {
    NAMES.forEach(function (name) {
        ['', '-journal', '-wal', '-shm'].forEach(function (suffix) {
            files.remove(files.path('./' + name + suffix));
        });
    });
}

function errorText(e) {
    return String(e && e.message !== undefined ? e.message : e).split('\n')[0];
}

function count(db) {
    return Number(db.rawQuery('SELECT COUNT(*) AS n FROM t', []).single().n);
}

// Creates `name` at version 2 with table t and `rows` rows.
function seed(name, rows) {
    var db = sqlite.open(name, { version: 2 });
    try {
        db.execSQL('CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT)');
        for (var i = 1; i <= rows; i++) {
            db.insert('t', { id: i, v: 'row' + i });
        }
        return db.getVersion();
    } finally {
        db.close();
    }
}

function run(key, body) {
    var out = {};
    try {
        body(out);
    } catch (e) {
        out.error = errorText(e);
    }
    result.cases[key] = out;
}

removeDatabases();
result.sdk = android.os.Build.VERSION.SDK_INT;

// A: no callback, version 2 -> 1 keeps the data; a later open at 2 upgrades 1 -> 2 as usual.
run('plain', function (out) {
    out.seeded = seed('p3-plain.db', 2);
    var db = sqlite.open('p3-plain.db', { version: 1 });
    try {
        out.version = db.getVersion();
        out.rows = count(db);
    } finally {
        db.close();
    }
    var upgrades = [];
    db = sqlite.open('p3-plain.db', { version: 2 }, {
        onUpgrade: function (d, from, to) {
            upgrades.push(from + '->' + to);
        },
    });
    try {
        out.upgrades = upgrades;
        out.reopened = db.getVersion();
    } finally {
        db.close();
    }
});

// B: a callback object without onDowngrade behaves as no callback; its other methods still run.
run('partial', function (out) {
    out.seeded = seed('p3-partial.db', 2);
    var events = [];
    var db = sqlite.open('p3-partial.db', { version: 1 }, {
        onCreate: function () {
            events.push('create');
        },
        onOpen: function () {
            events.push('open');
        },
    });
    try {
        out.events = events;
        out.version = db.getVersion();
        out.rows = count(db);
    } finally {
        db.close();
    }
});

// C: onDowngrade gets (database, oldVersion, newVersion) inside the helper's transaction, before onOpen;
// what it writes is kept and the version is set afterwards.
run('callback', function (out) {
    out.seeded = seed('p3-callback.db', 2);
    sqlite.open('p3-callback.db', { version: 3 }).close();
    var events = [];
    var db = sqlite.open('p3-callback.db', { version: 2 }, {
        onCreate: function () {
            events.push('create');
        },
        onUpgrade: function (d, from, to) {
            events.push('upgrade ' + from + '->' + to);
        },
        onDowngrade: function (d, from, to) {
            events.push('downgrade ' + from + '->' + to);
            out.argCount = arguments.length;
            out.argTypes = [typeof from, typeof to];
            out.databaseClass = String(d.getClass().getName());
            out.versionInside = d.getVersion();
            out.inTransaction = d.inTransaction();
            d.insert('t', { id: 3, v: 'from onDowngrade' });
        },
        onOpen: function (d) {
            events.push('open');
            out.versionInOpen = d.getVersion();
        },
    });
    try {
        out.events = events;
        out.version = db.getVersion();
        out.rows = count(db);
    } finally {
        db.close();
    }
});

// D: an exception from onDowngrade rolls the downgrade back and is thrown by sqlite.open (the way to keep the old
// behavior); the file keeps its version and rows, including a row the callback inserted before throwing.
run('throw', function (out) {
    out.seeded = seed('p3-throw.db', 2);
    out.thrown = false;
    try {
        sqlite.open('p3-throw.db', { version: 1 }, {
            onDowngrade: function (d) {
                d.insert('t', { id: 9, v: 'rolled back' });
                throw new Error('p3 refuses the downgrade');
            },
        }).close();
    } catch (e) {
        out.thrown = true;
        out.message = errorText(e);
    }
    var db = sqlite.open('p3-throw.db', { version: 2 });
    try {
        out.version = db.getVersion();
        out.rows = count(db);
    } finally {
        db.close();
    }
});

// E: readOnly does not change it (Android opens a writable connection unless that fails).
run('readonly', function (out) {
    out.seeded = seed('p3-readonly.db', 2);
    var db = sqlite.open('p3-readonly.db', { version: 1, readOnly: true });
    try {
        out.version = db.getVersion();
        out.rows = count(db);
    } finally {
        db.close();
    }
});

removeDatabases();
files.write(files.path('./result.json'), JSON.stringify(result));
console.log('AJ6P3SQLITE done');
