declare module "storage" {
  namespace storage {
    export type StorageValue = AutoJs6Node.JsonValue;

    export interface StorageStore {
      readonly name: string;
      get<T extends StorageValue = StorageValue>(key: string, defaultValue: T, options?: AutoJs6Node.BridgeCallOptions): Promise<StorageValue | T>;
      get<T extends StorageValue = StorageValue>(key: string, defaultValue?: T, options?: AutoJs6Node.BridgeCallOptions): Promise<StorageValue | T | undefined>;
      put(key: string, value: StorageValue, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      remove(key: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      clear(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      keys(options?: AutoJs6Node.BridgeCallOptions): Promise<readonly string[]>;
      contains(key: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
    }

    export interface StorageModule {
      create(name: string): StorageStore;
      open(name: string): StorageStore;
    }
  }

  const storage: storage.StorageModule;
  export = storage;
}
