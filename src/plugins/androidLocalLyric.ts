import { registerPlugin } from "@capacitor/core";

export interface AndroidLyricDirectory {
  uri: string;
  name: string;
}

export interface AndroidLyricPickResult extends Partial<AndroidLyricDirectory> {
  cancelled: boolean;
}

export interface AndroidLyricIndexEntry {
  uri: string;
  name: string;
  lastModified: number;
  directoryUri: string;
}

export interface AndroidLyricScanMeta {
  lastScanAt: number;
  totalFiles: number;
  matchedFiles: number;
  duplicateIds: number;
  failedFiles: number;
}

export interface AndroidLyricScanFailure {
  uri: string;
  name: string;
  reason: string;
  directoryUri: string;
}

export interface AndroidLyricScanSummary extends Omit<AndroidLyricScanMeta, "lastScanAt"> {
  indexMap: Record<string, AndroidLyricIndexEntry>;
  failures?: AndroidLyricScanFailure[];
}

export interface AndroidLocalLyricPlugin {
  pickLyricDirectory(): Promise<AndroidLyricPickResult>;
  scanLyricDirectories(options: {
    directories: AndroidLyricDirectory[];
  }): Promise<AndroidLyricScanSummary>;
  readLyricFile(options: { uri: string }): Promise<{ content: string }>;
}

export const AndroidLocalLyric = registerPlugin<AndroidLocalLyricPlugin>("AndroidLocalLyric");
