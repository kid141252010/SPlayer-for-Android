/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<object, object, any>;
  export default component;
}

interface NodeJsMobileChannel {
  on: (event: string, callback: (payload: unknown) => void) => void;
  post: (event: string, payload: unknown) => void;
  setListener: (callback: (payload: unknown) => void) => void;
  send: (payload: unknown) => void;
}

interface NodeJsMobileRuntime {
  start: (
    scriptFileName: string,
    callback: (error?: unknown) => void,
    options?: { redirectOutputToLogcat?: boolean },
  ) => void;
  startWithScript: (
    scriptBody: string,
    callback: (error?: unknown) => void,
    options?: { redirectOutputToLogcat?: boolean },
  ) => void;
  channel: NodeJsMobileChannel;
}

interface Window {
  nodejs?: NodeJsMobileRuntime;
  electron?: {
    ipcRenderer: {
      send: (channel: string, ...args: unknown[]) => void;
      sendSync: (channel: string, ...args: unknown[]) => any;
      invoke: <T = any>(channel: string, ...args: unknown[]) => Promise<T>;
      on: (channel: string, listener: (...args: any[]) => void) => void;
      removeAllListeners: (channel: string) => void;
      removeListener: (channel: string, listener: (...args: any[]) => void) => void;
    };
  };
}
