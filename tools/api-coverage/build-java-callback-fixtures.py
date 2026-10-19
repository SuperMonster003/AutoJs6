"""Build small owned JAR/DEX inputs for real runtime loading tests, using the installed JDK/SDK."""
from pathlib import Path
import argparse,os,subprocess,shutil,re,json,hashlib,zipfile
parser=argparse.ArgumentParser();parser.add_argument('--output',required=True);parser.add_argument('--sdk');parser.add_argument('--java-home');args=parser.parse_args()
root=Path(__file__).resolve().parents[2];output=Path(args.output).resolve();output.mkdir(parents=True,exist_ok=True)
sdk=args.sdk or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
 line=next(x for x in (root/'local.properties').read_text().splitlines() if x.startswith('sdk.dir='));sdk=line.split('=',1)[1].replace('\\:',':').replace('\\\\','\\')
sdk=Path(sdk);builds=sorted((sdk/'build-tools').iterdir(),key=lambda p:tuple(int(x) for x in re.findall(r'\d+',p.name)))
d8=next(p/('d8.bat' if os.name=='nt' else 'd8') for p in reversed(builds) if (p/('d8.bat' if os.name=='nt' else 'd8')).exists())
env=dict(os.environ)
if args.java_home:env['JAVA_HOME']=args.java_home
javac=str(Path(env['JAVA_HOME'])/'bin'/('javac.exe' if os.name=='nt' else 'javac')) if env.get('JAVA_HOME') else shutil.which('javac')
for name in ['CallbackFixture','DexFixture']:
 classes=output/(name+'-classes');classes.mkdir(exist_ok=True)
 subprocess.run([javac,'--release','8','-encoding','UTF-8','-d',str(classes),str(root/'tools/api-coverage/java-fixtures'/f'{name}.java')],check=True)
 jar=output/('callback.jar' if name=='CallbackFixture' else 'dex-input.jar')
 with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
  for p in sorted(classes.rglob('*.class')):archive.write(p,p.relative_to(classes).as_posix())
dexdir=output/'dex-output';dexdir.mkdir(exist_ok=True)
subprocess.run([str(d8),'--min-api','24','--output',str(dexdir),str(output/'dex-input.jar')],check=True,env=env)
shutil.copyfile(dexdir/'classes.dex',output/'fixture.dex')
proof={name:hashlib.sha256((output/name).read_bytes()).hexdigest() for name in ['callback.jar','fixture.dex']}
(output/'manifest.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof))
