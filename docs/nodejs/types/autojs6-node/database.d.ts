declare module "database" {
  namespace database {
    export type SqlParam = string | number | boolean | null;

    export interface OpenOptions extends AutoJs6Node.BridgeCallOptions {
      readonly name: string;
    }

    export interface RunResult {
      readonly rowsAffected: number | null;
      readonly insertId: number | null;
    }

    export type Row = Readonly<Record<string, AutoJs6Node.JsonValue>>;

    export interface TransactionOperation {
      readonly type: "exec" | "run" | "get" | "all";
      readonly sql: string;
      readonly params?: readonly SqlParam[];
    }

    export type TransactionResult = undefined | RunResult | Row | readonly Row[] | null;

    export interface DatabaseHandle {
      readonly id: string;
      readonly name: string;
      exec(sql: string, params?: readonly SqlParam[], options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      run(sql: string, params?: readonly SqlParam[], options?: AutoJs6Node.BridgeCallOptions): Promise<RunResult>;
      get(sql: string, params?: readonly SqlParam[], options?: AutoJs6Node.BridgeCallOptions): Promise<Row | null>;
      all(sql: string, params?: readonly SqlParam[], options?: AutoJs6Node.BridgeCallOptions): Promise<readonly Row[]>;
      transaction(operations: readonly TransactionOperation[], options?: AutoJs6Node.BridgeCallOptions): Promise<readonly TransactionResult[]>;
      close(options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
    }

    export interface DatabaseModule {
      open(name: string, options?: AutoJs6Node.BridgeCallOptions): Promise<DatabaseHandle>;
      open(options: OpenOptions): Promise<DatabaseHandle>;
    }
  }

  const database: database.DatabaseModule;
  export = database;
}
