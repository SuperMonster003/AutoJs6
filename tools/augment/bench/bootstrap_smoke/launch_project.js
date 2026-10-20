// Starts the project in PROJECT_DIR the way the app starts a project (ProjectLauncher applies project.json "useFeatures").
var service = org.autojs.autojs.AutoJs.getInstance().getScriptEngineService();
new org.autojs.autojs.project.ProjectLauncher('PROJECT_DIR').launch(service);
