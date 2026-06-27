var path = "out/rhino-compat-files.txt";
files.write(path, "hello from Rhino files");
var text = files.read(path);
toast(text);
files.remove(path);
