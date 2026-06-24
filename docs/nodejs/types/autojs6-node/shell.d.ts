declare module "shell" {
  namespace shell {
    export interface ShellExecOptions extends AutoJs6Node.BridgeCallOptions {
      root?: boolean;
      readonly shizuku?: never;
      cwd?: string;
      env?: Readonly<Record<string, string>>;
    }

    export interface ShellAccessOptions {
      root?: boolean;
      shizuku?: boolean;
    }

    export interface ShellAccessReport {
      readonly shell: boolean;
      readonly root: boolean;
      readonly shizuku: boolean;
      readonly missingCapabilities: readonly string[];
    }

    export interface ShellResult {
      readonly code: number;
      readonly stdout: string;
      readonly stderr: string;
    }

    export interface ShellModule {
      exec(command: string, options?: ShellExecOptions): Promise<ShellResult>;
      execRoot(command: string, options?: Omit<ShellExecOptions, "root">): Promise<ShellResult>;
      execShizuku(command: string, options?: Omit<ShellExecOptions, "root" | "shizuku">): Promise<never>;
      execFile(path: string, args?: readonly string[], options?: ShellExecOptions): Promise<ShellResult>;
      checkAccess(options?: ShellAccessOptions): ShellAccessReport;
      setDefaultTimeout(ms: number): number;
    }
  }

  const shell: shell.ShellModule;
  export = shell;
}
