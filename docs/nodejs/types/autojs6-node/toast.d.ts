declare module "toast" {
  namespace toast {
    export interface ToastOptions extends AutoJs6Node.BridgeCallOptions {
      duration?: "short" | "long" | number;
      log?: boolean;
    }

    export interface ToastModule {
      showToast(message: string, options?: ToastOptions): Promise<void>;
      toast(message: string, options?: ToastOptions): Promise<void>;
    }
  }

  const toast: toast.ToastModule;
  export = toast;
}
