import type { PluginListenerHandle } from '@capacitor/core';

export interface SimpleNativeSocketioPlugin {
  /**
   * Starts the socket connection.
   * @param options { url: string }
   */
  startSocketConnection(options: { url: string }): Promise<void>;

  /**
   * Dynamically registers a listener for the specified event.
   * @param options { event: string }
   */
  addSocketListener(options: { event: string }): Promise<void>;

  /**
   * Emits an event with the provided data.
   * @param options { event: string, data: any }
   */
  emit(options: { event: string; data: any }): Promise<void>;

  /**
   * Initializes event reception (keepAlive).
   */
  onSocketEvent(options?: {}): Promise<void>;

  /**
   * Notifies the native side that the webview (JS context) is now active.
   * Typically called when the app resumes.
   */
  setWebviewActive(options?: {}): Promise<void>;

  /**
   * Notifies the native side that the webview (JS context) is now inactive.
   * Typically called when the app is backgrounded.
   */
  setWebviewInactive(options?: {}): Promise<void>;

  setOnConnectEmit(options: { onConnectEvent: string; onConnectData: string }): Promise<void>;

  isConnected(): Promise<{ connected: boolean }>;

  addListener(
    eventName: string,
    listenerFunc: (data: object) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
}
