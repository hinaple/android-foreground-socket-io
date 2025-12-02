# simple-native-socket-io

a simple capacitor plugin to use socketio in native level

## Install

```bash
npm install simple-native-socket-io
npx cap sync
```

## API

<docgen-index>

* [`startSocketConnection(...)`](#startsocketconnection)
* [`addSocketListener(...)`](#addsocketlistener)
* [`emit(...)`](#emit)
* [`onSocketEvent(...)`](#onsocketevent)
* [`setWebviewActive(...)`](#setwebviewactive)
* [`setWebviewInactive(...)`](#setwebviewinactive)
* [`setOnConnectEmit(...)`](#setonconnectemit)
* [`isConnected()`](#isconnected)
* [`addListener(string, ...)`](#addlistenerstring-)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### startSocketConnection(...)

```typescript
startSocketConnection(options: { url: string; }) => Promise<void>
```

Starts the socket connection.

| Param         | Type                          | Description |
| ------------- | ----------------------------- | ----------- |
| **`options`** | <code>{ url: string; }</code> | : string }  |

--------------------


### addSocketListener(...)

```typescript
addSocketListener(options: { event: string; }) => Promise<void>
```

Dynamically registers a listener for the specified event.

| Param         | Type                            | Description |
| ------------- | ------------------------------- | ----------- |
| **`options`** | <code>{ event: string; }</code> | : string }  |

--------------------


### emit(...)

```typescript
emit(options: { event: string; data: any; }) => Promise<void>
```

Emits an event with the provided data.

| Param         | Type                                       | Description           |
| ------------- | ------------------------------------------ | --------------------- |
| **`options`** | <code>{ event: string; data: any; }</code> | : string, data: any } |

--------------------


### onSocketEvent(...)

```typescript
onSocketEvent(options?: {} | undefined) => Promise<void>
```

Initializes event reception (keepAlive).

| Param         | Type            |
| ------------- | --------------- |
| **`options`** | <code>{}</code> |

--------------------


### setWebviewActive(...)

```typescript
setWebviewActive(options?: {} | undefined) => Promise<void>
```

Notifies the native side that the webview (JS context) is now active.
Typically called when the app resumes.

| Param         | Type            |
| ------------- | --------------- |
| **`options`** | <code>{}</code> |

--------------------


### setWebviewInactive(...)

```typescript
setWebviewInactive(options?: {} | undefined) => Promise<void>
```

Notifies the native side that the webview (JS context) is now inactive.
Typically called when the app is backgrounded.

| Param         | Type            |
| ------------- | --------------- |
| **`options`** | <code>{}</code> |

--------------------


### setOnConnectEmit(...)

```typescript
setOnConnectEmit(options: { onConnectEvent: string; onConnectData: string; }) => Promise<void>
```

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code>{ onConnectEvent: string; onConnectData: string; }</code> |

--------------------


### isConnected()

```typescript
isConnected() => Promise<{ connected: boolean; }>
```

**Returns:** <code>Promise&lt;{ connected: boolean; }&gt;</code>

--------------------


### addListener(string, ...)

```typescript
addListener(eventName: string, listenerFunc: (data: object) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                   |
| ------------------ | -------------------------------------- |
| **`eventName`**    | <code>string</code>                    |
| **`listenerFunc`** | <code>(data: object) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |

</docgen-api>
