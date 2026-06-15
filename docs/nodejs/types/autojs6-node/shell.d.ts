declare module "shell" {
  namespace shell {
    export interface ShellExecOptions extends AutoJs6Node.BridgeCallOptions {
      root?: boolean;
      cwd?: string;
      env?: Readonly<Record<string, string>>;
    }

    export interface ShellResult {
      readonly code: number;
      readonly stdout: string;
      readonly stderr: string;
    }

    export interface ShellModule {
      exec(command: string, options?: ShellExecOptions): Promise<ShellResult>;
    }
  }

  const shell: shell.ShellModule;
  export = shell;
}
