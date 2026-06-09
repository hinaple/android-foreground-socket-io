import type { PluginListenerHandle } from '@capacitor/core';

export interface AndroidForegroundSocketStartOptions {
  socket: AndroidForegroundSocketOptions;
  listen?: AndroidForegroundSocketListenOptions;
  service?: AndroidForegroundSocketServiceOptions;
  onConnectEmit?: AndroidForegroundSocketEmitOptions;
  actions?: AndroidForegroundSocketAction[];
}

export interface AndroidForegroundSocketOptions {
  url: string;
  path?: string;
  transports?: string[];
  reconnect?: boolean;
  reconnectionAttempts?: number;
  reconnectionDelay?: number;
  reconnectionDelayMax?: number;
  timeout?: number;
  forceNew?: boolean;
  query?: string;
}

export interface AndroidForegroundSocketListenOptions {
  events?: string[];
  bufferSize?: number;
}

export interface AndroidForegroundSocketServiceOptions {
  notificationTitle?: string;
  notificationText?: string;
  startOnBoot?: boolean;
}

export interface AndroidForegroundSocketEmitOptions {
  event: string;
  data?: any;
}

export interface AndroidForegroundSocketAction {
  id: string;
  event: string;
  enabled?: boolean;
  match?: AndroidForegroundSocketMatch;
  cooldownMs?: number;
  run: AndroidForegroundSocketNativeAction[];
}

export type AndroidForegroundSocketMatch =
  | { path: string; equals: any }
  | { path: string; contains: any }
  | { path: string; exists: boolean }
  | { path: string; gt: number }
  | { path: string; gte: number }
  | { path: string; lt: number }
  | { path: string; lte: number }
  | { all: AndroidForegroundSocketMatch[] }
  | { any: AndroidForegroundSocketMatch[] };

export type AndroidForegroundSocketNativeAction =
  | { type: 'wakeScreen'; durationMs?: number }
  | { type: 'vibrate'; durationMs: number }
  | { type: 'vibrate'; pattern: number[]; repeat?: number }
  | { type: 'playSound'; source: string; volume?: number; loop?: boolean; channel?: string }
  | { type: 'stopSound'; channel?: string }
  | { type: 'setVolume'; level: number }
  | { type: 'delay'; durationMs: number }
  | { type: 'emit'; event: string; data?: any }
  | { type: 'notifyWebview'; event?: string; data?: any };

export interface AndroidForegroundSocketStatus {
  serviceRunning: boolean;
  connected: boolean;
  url?: string;
  registeredActions: number;
  watchedEvents: string[];
  webviewActive: boolean;
  queuedEvents: number;
}

export interface AndroidForegroundSocketPlugin {
  start(options: AndroidForegroundSocketStartOptions): Promise<void>;
  stop(): Promise<void>;
  restart(options?: AndroidForegroundSocketStartOptions): Promise<void>;
  connect(): Promise<void>;
  disconnect(): Promise<void>;
  watchEvent(options: { event: string }): Promise<void>;
  unwatchEvent(options: { event: string }): Promise<void>;
  emit(options: AndroidForegroundSocketEmitOptions): Promise<void>;
  registerAction(action: AndroidForegroundSocketAction): Promise<void>;
  unregisterAction(options: { id: string }): Promise<void>;
  clearActions(): Promise<void>;
  listActions(): Promise<{ actions: AndroidForegroundSocketAction[] }>;
  setActionEnabled(options: { id: string; enabled: boolean }): Promise<void>;
  setWebviewActive(): Promise<void>;
  setWebviewInactive(): Promise<void>;
  setOnConnectEmit(options: AndroidForegroundSocketEmitOptions): Promise<void>;
  getStatus(): Promise<AndroidForegroundSocketStatus>;
  addListener(
    eventName: string,
    listenerFunc: (data: any) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
}
