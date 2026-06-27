var store = storages.create("rhino-compat");
store.put("enabled", true);
toast(String(store.get("enabled", false)));
store.remove("enabled");
