# AndroidForegroundSocket

AndroidForegroundSocket은 Android 전용 Capacitor 플러그인입니다. WebView가 일시 중지되거나 종료된 상태에서도 Android foreground service에서 Socket.IO 연결을 유지하고, 수신한 이벤트에 따라 화면 켜기, 진동, 오디오 재생 같은 네이티브 동작을 실행할 수 있습니다.

방탈출 장비나 전용 컨트롤러 기기처럼 Play Store 배포보다 장시간 연결 유지와 즉각적인 네이티브 반응이 중요한 환경을 기준으로 설계되었습니다.

## 설치

```bash
npm install android-foreground-socket-io
npx cap sync
```

## 기본 개념

Socket.IO 연결은 JavaScript가 아니라 Android native service가 소유합니다.

JavaScript는 다음 역할만 담당합니다.

- Socket.IO 서버 연결 설정
- JavaScript로 전달받을 Socket.IO 이벤트 등록
- 네이티브에서 실행할 액션 등록
- WebView 활성/비활성 상태 알림
- 상태 조회와 수동 emit 호출

WebView가 살아 있으면 이벤트는 즉시 JavaScript listener로 전달됩니다. WebView가 비활성 상태이면 이벤트는 native queue에 순서대로 저장되고, `setWebviewActive()`가 호출될 때 저장된 순서 그대로 전달됩니다.

## 빠른 시작

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

## JavaScript에서 Socket.IO 이벤트 받기

`start()`의 `listen.events`에 이벤트명을 등록하면 해당 Socket.IO 이벤트가 JavaScript로 전달됩니다.

```ts
await AndroidForegroundSocket.start({
  socket: {
    url: 'http://192.168.0.10:3000',
    transports: ['websocket'],
  },
  listen: {
    events: ['cue'],
    bufferSize: 100,
  },
});
```

실행 중에 이벤트를 추가로 등록할 수도 있습니다.

```ts
await AndroidForegroundSocket.watchEvent({ event: 'cue' });
```

이벤트는 두 가지 listener 채널로 전달됩니다.

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

`socketEvent`는 등록된 모든 Socket.IO 이벤트를 받습니다. `cue`처럼 이벤트명을 직접 넣은 listener는 해당 이벤트만 받습니다.

기본 Socket.IO 생명주기 이벤트인 `connect`, `disconnect`, `connect_error`도 JavaScript로 전달됩니다.

## WebView 상태와 이벤트 버퍼링

앱의 생명주기에 맞춰 WebView 상태를 native에 알려야 합니다.

```ts
document.addEventListener('resume', () => {
  AndroidForegroundSocket.setWebviewActive();
});

document.addEventListener('pause', () => {
  AndroidForegroundSocket.setWebviewInactive();
});
```

WebView가 active 상태이면 이벤트는 즉시 전달됩니다. inactive 상태이면 이벤트는 native queue에 저장됩니다. 이후 `setWebviewActive()`가 호출되면 저장된 이벤트가 수신 순서대로 JavaScript listener에 전달됩니다.

버퍼 크기는 `listen.bufferSize`로 설정합니다. 버퍼가 가득 차면 가장 오래된 이벤트부터 제거됩니다.

## 네이티브 액션

네이티브 액션은 WebView가 멈춰 있어도 Android service 안에서 실행됩니다. 액션은 `start()` 시점에 함께 등록하거나, 실행 중에 `registerAction()`으로 추가할 수 있습니다.

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

지원하는 액션 타입은 다음과 같습니다.

| 타입 | 설명 |
| --- | --- |
| `wakeScreen` | 네이티브 Activity를 열어 화면을 켭니다. `durationMs` 이후 자동 종료됩니다. |
| `vibrate` | `durationMs`로 한 번 진동하거나, `pattern`으로 진동 패턴을 실행합니다. |
| `playSound` | `asset://`, `file://`, `url://`, `http://`, `https://` 음원을 재생합니다. |
| `stopSound` | 지정한 `channel`의 오디오를 중지합니다. 기본 채널은 `default`입니다. |
| `setVolume` | 음악 스트림 볼륨을 설정합니다. `level`은 `0`부터 `1` 사이 값입니다. |
| `delay` | 다음 액션 실행 전 대기합니다. |
| `emit` | native에서 Socket.IO 이벤트를 emit합니다. |
| `notifyWebview` | JavaScript listener로 커스텀 이벤트를 보냅니다. 이 이벤트도 WebView 상태에 따라 버퍼링됩니다. |

## 액션 조건

액션은 `match`로 실행 조건을 지정할 수 있습니다. `path`는 Socket.IO payload를 기준으로 dot notation을 사용합니다.

```ts
{ path: 'name', equals: 'door_open' }
{ path: 'level', gt: 3 }
{ path: 'tags', contains: 'urgent' }
{ path: 'enabled', exists: true }
{ all: [{ path: 'type', equals: 'cue' }, { path: 'room', equals: 'A' }] }
{ any: [{ path: 'name', equals: 'start' }, { path: 'name', equals: 'reset' }] }
```

`match`가 없으면 Socket.IO 이벤트명이 일치할 때마다 액션이 실행됩니다.

`cooldownMs`를 지정하면 같은 액션이 너무 자주 실행되지 않도록 제한할 수 있습니다.

```ts
{
  id: 'door-open',
  event: 'cue',
  match: { path: 'name', equals: 'door_open' },
  cooldownMs: 3000,
  run: [{ type: 'wakeScreen', durationMs: 8000 }],
}
```

## 런타임 제어

Socket.IO 이벤트를 직접 emit할 수 있습니다.

```ts
await AndroidForegroundSocket.emit({
  event: 'controller:message',
  data: { value: 1 },
});
```

현재 상태를 조회할 수 있습니다.

```ts
const status = await AndroidForegroundSocket.getStatus();

console.log(status.connected);
console.log(status.queuedEvents);
console.log(status.registeredActions);
```

액션을 켜거나 끌 수 있습니다.

```ts
await AndroidForegroundSocket.setActionEnabled({
  id: 'alarm',
  enabled: false,
});
```

액션을 제거하거나 전체 삭제할 수 있습니다.

```ts
await AndroidForegroundSocket.unregisterAction({ id: 'alarm' });
await AndroidForegroundSocket.clearActions();
```

연결을 수동으로 제어할 수 있습니다.

```ts
await AndroidForegroundSocket.disconnect();
await AndroidForegroundSocket.connect();
await AndroidForegroundSocket.stop();
```

## Listener Payload

Socket.IO 이벤트 listener는 다음 형태의 payload를 받습니다.

```ts
{
  event: string;
  data: any;
  receivedAt: number;
}
```

`status` listener는 `AndroidForegroundSocketStatus` 형태의 payload를 받습니다.

```ts
AndroidForegroundSocket.addListener('status', status => {
  console.log(status.connected);
  console.log(status.queuedEvents);
});
```

액션 실행 결과는 `actionExecuted` listener로 받을 수 있습니다.

```ts
AndroidForegroundSocket.addListener('actionExecuted', event => {
  console.log(event.id);
});
```

## API 요약

| 메서드 | 설명 |
| --- | --- |
| `start(options)` | foreground service를 시작하고 Socket.IO 연결, listener, action을 설정합니다. |
| `stop()` | Socket.IO 연결을 종료하고 foreground service를 중지합니다. |
| `restart(options?)` | 기존 설정 또는 새 설정으로 service를 다시 시작합니다. |
| `connect()` | Socket.IO 연결을 수동으로 시작합니다. |
| `disconnect()` | Socket.IO 연결을 수동으로 끊습니다. |
| `watchEvent({ event })` | JavaScript로 전달받을 Socket.IO 이벤트를 등록합니다. |
| `unwatchEvent({ event })` | 등록한 Socket.IO 이벤트 전달을 해제합니다. |
| `emit({ event, data })` | native socket에서 Socket.IO 이벤트를 emit합니다. |
| `registerAction(action)` | 네이티브 액션을 등록합니다. |
| `unregisterAction({ id })` | 액션을 제거합니다. |
| `clearActions()` | 등록된 모든 액션을 제거합니다. |
| `listActions()` | 등록된 액션 목록을 조회합니다. |
| `setActionEnabled({ id, enabled })` | 액션 활성 상태를 변경합니다. |
| `setWebviewActive()` | WebView가 활성 상태임을 native에 알리고 버퍼를 flush합니다. |
| `setWebviewInactive()` | WebView가 비활성 상태임을 native에 알립니다. |
| `setOnConnectEmit({ event, data })` | Socket.IO 연결 성공 시 자동 emit할 이벤트를 설정합니다. |
| `getStatus()` | service, socket, queue 상태를 조회합니다. |
| `addListener(eventName, handler)` | Capacitor listener를 등록합니다. |

## 주의사항

이 플러그인은 Android 전용입니다. Web과 iOS 구현은 제공하지 않습니다.

실제 화면 켜기, 진동, 오디오 재생, 장시간 foreground service 유지 동작은 Android 기기별 전원 관리 정책의 영향을 받을 수 있습니다. 전용 장비에서는 배터리 최적화 제외, 화면 잠금 정책, 자동 실행 정책을 함께 설정하는 것이 좋습니다.
