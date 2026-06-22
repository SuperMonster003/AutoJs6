declare module "sqlite" {
  import database = require("database");

  namespace sqlite {
    export interface SQLiteModule {
      (name: string, options?: AutoJs6Node.BridgeCallOptions): Promise<database.DatabaseHandle>;
      (options: database.OpenOptions): Promise<database.DatabaseHandle>;
      open(name: string, options?: AutoJs6Node.BridgeCallOptions): Promise<database.DatabaseHandle>;
      open(options: database.OpenOptions): Promise<database.DatabaseHandle>;
    }
  }

  const sqlite: sqlite.SQLiteModule;
  export = sqlite;
}
