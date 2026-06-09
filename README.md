# android-foreground-socket-io

Android foreground Socket.IO plugin for Capacitor

## Install

```bash
npm install android-foreground-socket-io
npx cap sync
```

## Overview

`AndroidForegroundSocket` is an Android-only Capacitor plugin for keeping a Socket.IO connection alive in a native foreground service. It is designed for dedicated controller devices where the WebView may be paused or stopped while the Android service must continue receiving Socket.IO events.

The native layer owns the Socket.IO connection. JavaScript configures the service, registers which Socket.IO events should be forwarded to the WebView, and optionally registers native actions that run without WebView involvement.

When the WebView is active, forwarded Socket.IO events are delivered immediately through Capacitor listeners. When the WebView is inactive, events are stored in a FIFO buffer and emitted in order when `setWebviewActive()` is called.

## Basic Usage

```ts
import { AndroidForegroundSocket } from 'android-foreground-socket-io';

await AndroidForegroundSocket.start({
  socket: {
    url: 'http://192.168.0.10:3000',
    transports: ['websocket'],
    reconnect: true,
  },
  listen: {
    events: ['cue', 'timer', 'room:event'],
    bufferSize: 100,
  },
  service: {
    notificationTitle: 'Room Controller',
    notificationText: 'Socket connection is running',
    startOnBoot: false,
  },
  onConnectEmit: {
    event: 'controller:ready',
    data: { deviceId: 'tablet-1' },
  },
  actions: [
    {
      id: 'door-open',
      event: 'cue',
      match: { path: 'name', equals: 'door_open' },
      cooldownMs: 1000,
      run: [
        { type: 'wakeScreen', durationMs: 8000 },
        { type: 'vibrate', durationMs: 500 },
        { type: 'playSound', source: 'asset://sounds/door.mp3', volume: 1 },
      ],
    },
  ],
});
```

## Listening To Socket.IO Events In JavaScript

Register Socket.IO event names with `listen.events` during `start()`, or add them later with `watchEvent()`.

```ts
await AndroidForegroundSocket.watchEvent({ event: 'cue' });
```

The plugin emits forwarded Socket.IO events in two listener channels:

```ts
const allHandle = await AndroidForegroundSocket.addListener('socketEvent', event => {
  console.log(event.event);
  console.log(event.data);
  console.log(event.receivedAt);
});

const cueHandle = await AndroidForegroundSocket.addListener('cue', event => {
  console.log(event.data);
});
```

`socketEvent` receives every watched Socket.IO event. The event-name listener, such as `cue`, receives only that specific Socket.IO event.

The default Socket.IO lifecycle events `connect`, `disconnect`, and `connect_error` are also forwarded.

## WebView Activity State

Call these from your app lifecycle hooks:

```ts
document.addEventListener('resume', () => {
  AndroidForegroundSocket.setWebviewActive();
});

document.addEventListener('pause', () => {
  AndroidForegroundSocket.setWebviewInactive();
});
```

When inactive, forwarded events are queued. When active again, queued events are flushed in the same order they were received.

## Native Actions

Native actions run inside Android even if the WebView is paused. They can be registered at startup or later.

```ts
await AndroidForegroundSocket.registerAction({
  id: 'alarm',
  event: 'room:event',
  match: {
    all: [
      { path: 'type', equals: 'alarm' },
      { path: 'room', equals: 'A' },
    ],
  },
  run: [
    { type: 'setVolume', level: 1 },
    { type: 'wakeScreen', durationMs: 10000 },
    { type: 'playSound', source: 'url://http://192.168.0.10/sounds/alarm.mp3' },
    { type: 'vibrate', pattern: [0, 300, 100, 300] },
    { type: 'emit', event: 'controller:ack', data: { action: 'alarm' } },
  ],
});
```

Supported action types:

| Type | Description |
| --- | --- |
| `wakeScreen` | Opens a native activity that turns the screen on for `durationMs`. |
| `vibrate` | Vibrates once with `durationMs`, or uses a vibration `pattern`. |
| `playSound` | Plays audio from `asset://`, `file://`, `url://`, `http://`, or `https://`. |
| `stopSound` | Stops audio on a named `channel`; default channel is `default`. |
| `setVolume` | Sets the music stream volume with `level` from `0` to `1`. |
| `delay` | Waits before running the next action step. |
| `emit` | Emits a Socket.IO event from native code. |
| `notifyWebview` | Publishes a custom event to JavaScript using the same buffering rules. |

## Match Rules

Actions can be filtered with `match`. Paths use dot notation and are resolved against the Socket.IO payload.

```ts
{ path: 'name', equals: 'door_open' }
{ path: 'level', gt: 3 }
{ path: 'tags', contains: 'urgent' }
{ path: 'enabled', exists: true }
{ all: [{ path: 'type', equals: 'cue' }, { path: 'room', equals: 'A' }] }
{ any: [{ path: 'name', equals: 'start' }, { path: 'name', equals: 'reset' }] }
```

If an action has no `match`, it runs for every event with the matching Socket.IO event name.

## Runtime Control

```ts
await AndroidForegroundSocket.emit({
  event: 'controller:message',
  data: { value: 1 },
});

const status = await AndroidForegroundSocket.getStatus();

await AndroidForegroundSocket.setActionEnabled({
  id: 'alarm',
  enabled: false,
});

await AndroidForegroundSocket.unregisterAction({ id: 'alarm' });
await AndroidForegroundSocket.clearActions();
await AndroidForegroundSocket.disconnect();
await AndroidForegroundSocket.connect();
await AndroidForegroundSocket.stop();
```

## Event Payload Shape

Forwarded Socket.IO listener payloads have this shape:

```ts
{
  event: string;
  data: any;
  receivedAt: number;
}
```

Status listener payloads match `AndroidForegroundSocketStatus`.

```ts
AndroidForegroundSocket.addListener('status', status => {
  console.log(status.connected);
  console.log(status.queuedEvents);
});
```

Action execution events are emitted on `actionExecuted`.

```ts
AndroidForegroundSocket.addListener('actionExecuted', event => {
  console.log(event.id);
});
```

## API

<docgen-index>

* [`start(...)`](#start)
* [`stop()`](#stop)
* [`restart(...)`](#restart)
* [`connect()`](#connect)
* [`disconnect()`](#disconnect)
* [`watchEvent(...)`](#watchevent)
* [`unwatchEvent(...)`](#unwatchevent)
* [`emit(...)`](#emit)
* [`registerAction(...)`](#registeraction)
* [`unregisterAction(...)`](#unregisteraction)
* [`clearActions()`](#clearactions)
* [`listActions()`](#listactions)
* [`setActionEnabled(...)`](#setactionenabled)
* [`setWebviewActive()`](#setwebviewactive)
* [`setWebviewInactive()`](#setwebviewinactive)
* [`setOnConnectEmit(...)`](#setonconnectemit)
* [`getStatus()`](#getstatus)
* [`addListener(string, ...)`](#addlistenerstring-)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### start(...)

```typescript
start(options: AndroidForegroundSocketStartOptions) => Promise<void>
```

| Param         | Type                                                                                                |
| ------------- | --------------------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#androidforegroundsocketstartoptions">AndroidForegroundSocketStartOptions</a></code> |

--------------------


### stop()

```typescript
stop() => Promise<void>
```

--------------------


### restart(...)

```typescript
restart(options?: AndroidForegroundSocketStartOptions | undefined) => Promise<void>
```

| Param         | Type                                                                                                |
| ------------- | --------------------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#androidforegroundsocketstartoptions">AndroidForegroundSocketStartOptions</a></code> |

--------------------


### connect()

```typescript
connect() => Promise<void>
```

--------------------


### disconnect()

```typescript
disconnect() => Promise<void>
```

--------------------


### watchEvent(...)

```typescript
watchEvent(options: { event: string; }) => Promise<void>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ event: string; }</code> |

--------------------


### unwatchEvent(...)

```typescript
unwatchEvent(options: { event: string; }) => Promise<void>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ event: string; }</code> |

--------------------


### emit(...)

```typescript
emit(options: AndroidForegroundSocketEmitOptions) => Promise<void>
```

| Param         | Type                                                                                              |
| ------------- | ------------------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#androidforegroundsocketemitoptions">AndroidForegroundSocketEmitOptions</a></code> |

--------------------


### registerAction(...)

```typescript
registerAction(action: AndroidForegroundSocketAction) => Promise<void>
```

| Param        | Type                                                                                    |
| ------------ | --------------------------------------------------------------------------------------- |
| **`action`** | <code><a href="#androidforegroundsocketaction">AndroidForegroundSocketAction</a></code> |

--------------------


### unregisterAction(...)

```typescript
unregisterAction(options: { id: string; }) => Promise<void>
```

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

--------------------


### clearActions()

```typescript
clearActions() => Promise<void>
```

--------------------


### listActions()

```typescript
listActions() => Promise<{ actions: AndroidForegroundSocketAction[]; }>
```

**Returns:** <code>Promise&lt;{ actions: AndroidForegroundSocketAction[]; }&gt;</code>

--------------------


### setActionEnabled(...)

```typescript
setActionEnabled(options: { id: string; enabled: boolean; }) => Promise<void>
```

| Param         | Type                                           |
| ------------- | ---------------------------------------------- |
| **`options`** | <code>{ id: string; enabled: boolean; }</code> |

--------------------


### setWebviewActive()

```typescript
setWebviewActive() => Promise<void>
```

--------------------


### setWebviewInactive()

```typescript
setWebviewInactive() => Promise<void>
```

--------------------


### setOnConnectEmit(...)

```typescript
setOnConnectEmit(options: AndroidForegroundSocketEmitOptions) => Promise<void>
```

| Param         | Type                                                                                              |
| ------------- | ------------------------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#androidforegroundsocketemitoptions">AndroidForegroundSocketEmitOptions</a></code> |

--------------------


### getStatus()

```typescript
getStatus() => Promise<AndroidForegroundSocketStatus>
```

**Returns:** <code>Promise&lt;<a href="#androidforegroundsocketstatus">AndroidForegroundSocketStatus</a>&gt;</code>

--------------------


### addListener(string, ...)

```typescript
addListener(eventName: string, listenerFunc: (data: any) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                |
| ------------------ | ----------------------------------- |
| **`eventName`**    | <code>string</code>                 |
| **`listenerFunc`** | <code>(data: any) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### Interfaces


#### AndroidForegroundSocketStartOptions

| Prop                | Type                                                                                                    |
| ------------------- | ------------------------------------------------------------------------------------------------------- |
| **`socket`**        | <code><a href="#androidforegroundsocketoptions">AndroidForegroundSocketOptions</a></code>               |
| **`listen`**        | <code><a href="#androidforegroundsocketlistenoptions">AndroidForegroundSocketListenOptions</a></code>   |
| **`service`**       | <code><a href="#androidforegroundsocketserviceoptions">AndroidForegroundSocketServiceOptions</a></code> |
| **`onConnectEmit`** | <code><a href="#androidforegroundsocketemitoptions">AndroidForegroundSocketEmitOptions</a></code>       |
| **`actions`**       | <code>AndroidForegroundSocketAction[]</code>                                                            |


#### AndroidForegroundSocketOptions

| Prop                       | Type                  |
| -------------------------- | --------------------- |
| **`url`**                  | <code>string</code>   |
| **`path`**                 | <code>string</code>   |
| **`transports`**           | <code>string[]</code> |
| **`reconnect`**            | <code>boolean</code>  |
| **`reconnectionAttempts`** | <code>number</code>   |
| **`reconnectionDelay`**    | <code>number</code>   |
| **`reconnectionDelayMax`** | <code>number</code>   |
| **`timeout`**              | <code>number</code>   |
| **`forceNew`**             | <code>boolean</code>  |
| **`query`**                | <code>string</code>   |


#### AndroidForegroundSocketListenOptions

| Prop             | Type                  |
| ---------------- | --------------------- |
| **`events`**     | <code>string[]</code> |
| **`bufferSize`** | <code>number</code>   |


#### AndroidForegroundSocketServiceOptions

| Prop                    | Type                 |
| ----------------------- | -------------------- |
| **`notificationTitle`** | <code>string</code>  |
| **`notificationText`**  | <code>string</code>  |
| **`startOnBoot`**       | <code>boolean</code> |


#### AndroidForegroundSocketEmitOptions

| Prop        | Type                |
| ----------- | ------------------- |
| **`event`** | <code>string</code> |
| **`data`**  | <code>any</code>    |


#### AndroidForegroundSocketAction

| Prop             | Type                                                                                  |
| ---------------- | ------------------------------------------------------------------------------------- |
| **`id`**         | <code>string</code>                                                                   |
| **`event`**      | <code>string</code>                                                                   |
| **`enabled`**    | <code>boolean</code>                                                                  |
| **`match`**      | <code><a href="#androidforegroundsocketmatch">AndroidForegroundSocketMatch</a></code> |
| **`cooldownMs`** | <code>number</code>                                                                   |
| **`run`**        | <code>AndroidForegroundSocketNativeAction[]</code>                                    |


#### AndroidForegroundSocketStatus

| Prop                    | Type                  |
| ----------------------- | --------------------- |
| **`serviceRunning`**    | <code>boolean</code>  |
| **`connected`**         | <code>boolean</code>  |
| **`url`**               | <code>string</code>   |
| **`registeredActions`** | <code>number</code>   |
| **`watchedEvents`**     | <code>string[]</code> |
| **`webviewActive`**     | <code>boolean</code>  |
| **`queuedEvents`**      | <code>number</code>   |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### AndroidForegroundSocketMatch

<code>{ path: string; equals: any } | { path: string; contains: any } | { path: string; exists: boolean } | { path: string; gt: number } | { path: string; gte: number } | { path: string; lt: number } | { path: string; lte: number } | { all: AndroidForegroundSocketMatch[] } | { any: AndroidForegroundSocketMatch[] }</code>


#### AndroidForegroundSocketNativeAction

<code>{ type: 'wakeScreen'; durationMs?: number } | { type: 'vibrate'; durationMs: number } | { type: 'vibrate'; pattern: number[]; repeat?: number } | { type: 'playSound'; source: string; volume?: number; loop?: boolean; channel?: string } | { type: 'stopSound'; channel?: string } | { type: 'setVolume'; level: number } | { type: 'delay'; durationMs: number } | { type: 'emit'; event: string; data?: any } | { type: 'notifyWebview'; event?: string; data?: any }</code>

</docgen-api>
