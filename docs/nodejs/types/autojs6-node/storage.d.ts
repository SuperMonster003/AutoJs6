declare module "storage" {
  namespace storage {
    export type StorageValue = AutoJs6Node.JsonValue;

    export interface StorageStore {
      readonly name: string;
      get<T extends StorageValue = StorageValue>(key: string, defaultValue: T, options?: AutoJs6Node.BridgeCallOptions): Promise<StorageValue | T>;
      get<T extends StorageValue = StorageValue>(key: string, defaultValue?: T, options?: AutoJs6Node.BridgeCallOptions): Promise<StorageValue | T | undefined>;
      put(key: string, value: StorageValue, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      putSync(key: string, value: StorageValue, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      remove(key: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      removeSync(key: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      clear(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      clearSync(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      keys(options?: AutoJs6Node.BridgeCallOptions): Promise<readonly string[]>;
      contains(key: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;
      selfRemove(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      selfRemoveSync(options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
    }

    export interface StorageModule {
      create(name: string): StorageStore;
      open(name: string): StorageStore;
      remove(name: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      removeSync(name: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;
      names(): never;
      all(): never;
    }
  }

  const storage: storage.StorageModule;
  export = storage;
}
