import { registerPlugin } from '@capacitor/core';

import type { AndroidForegroundSocketPlugin } from './definitions';

const AndroidForegroundSocket = registerPlugin<AndroidForegroundSocketPlugin>('AndroidForegroundSocket');

export * from './definitions';
export { AndroidForegroundSocket };
