// The host's registered script names, as the same run of check.py wrote them to exports.json (extract_exports.py
// reads the KSP script-modules.json). The structural check-runtime-*.mjs scripts take the host method sets from here
// instead of parsing Kotlin registrations; run check.py first (CI runs them after it, in the same job).
import {existsSync, readFileSync, statSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const host = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const RUN_CHECK = 'run `python tools/api-coverage/check.py` first (it needs script-modules.json from `gradlew :app:kspAppDebugKotlin`)';

export function hostExports(file = process.env.AUTOJS6_API_COVERAGE_EXPORTS || resolve(host, 'build/reports/api-coverage/exports.json')) {
  if (!existsSync(file)) throw Error(`${file} is missing; ${RUN_CHECK}`);
  const data = JSON.parse(readFileSync(file, 'utf8'));
  if (data.dataSource?.kind !== 'ksp') throw Error(`${file} was read from ${data.dataSource?.kind}, not script-modules.json; ${RUN_CHECK}`);
  const metadata = resolve(host, data.dataSource.file);
  if (existsSync(metadata) && statSync(metadata).mtimeMs > statSync(file).mtimeMs) throw Error(`${file} is older than ${metadata}; ${RUN_CHECK}`);
  const records = (module, kind) => data.records.filter(row => row.module === module && (!kind || row.kind === kind));
  const names = (module, kind) => {
    const found = records(module, kind).map(row => row.member).sort();
    if (!found.length) throw Error(`${file}: no ${kind || 'records'} for module "${module}"`);
    return found;
  };
  return {
    file,
    module: name => data.modules.find(row => row.module === name),
    /** Member names of one kind (Functions, Getters, Properties, JavaClasses) placed on a module, sorted. */
    names,
    functions: module => names(module, 'Functions'),
    returnedObject: name => data.returnedObjects.find(row => row.object === name),
  };
}
