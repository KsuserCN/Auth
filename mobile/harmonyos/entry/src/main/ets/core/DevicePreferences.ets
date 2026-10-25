import { common, ConfigurationConstant } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';

// Only public device preferences belong here. Session material stays in SecureSessionStore.
let store: preferences.Preferences | null = null;
let writes: Promise<void> = Promise.resolve();

function colorMode(index: number): ConfigurationConstant.ColorMode {
  return index === 1 ? ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT : index === 2 ?
    ConfigurationConstant.ColorMode.COLOR_MODE_DARK : ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET;
}

export async function initializeDevicePreferences(context: common.UIAbilityContext): Promise<void> {
  store = await preferences.getPreferences(context, { name: 'device-ui-preferences' });
  const saved = await store.get('appearance', 0);
  const appearance = typeof saved === 'number' && [0, 1, 2].includes(saved) ? saved : 0;
  const accepted = await store.get('agreementAccepted', false);
  context.getApplicationContext().setColorMode(colorMode(appearance));
  AppStorage.setOrCreate<number>('appearance', appearance);
  AppStorage.setOrCreate<boolean>('agreementAccepted', accepted === true);
}

async function save(key: string, value: preferences.ValueType): Promise<void> {
  const task = writes.then(async () => {
    if (!store) { throw new Error('设备偏好尚未初始化'); }
    await store.put(key, value);
    await store.flush();
  });
  writes = task.catch(() => {});
  await task;
}

export async function setAppearance(context: common.UIAbilityContext, index: number): Promise<void> {
  if (![0, 1, 2].includes(index)) { return; }
  await save('appearance', index);
  context.getApplicationContext().setColorMode(colorMode(index));
  AppStorage.setOrCreate<number>('appearance', index);
}

export async function setAgreementAccepted(value: boolean): Promise<void> {
  // The user's current choice remains valid even if saving this non-secret device preference fails.
  AppStorage.setOrCreate<boolean>('agreementAccepted', value);
  await save('agreementAccepted', value);
}
