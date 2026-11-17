import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Small preferences kept on this device (not the account), like which way the shelf is shown: in localStorage on
 * the web, and the device's secure store on a phone (already used for the sign-in).
 */
export async function getPref(key: string): Promise<string | null> {
  try {
    if (Platform.OS === 'web') return globalThis.localStorage?.getItem(key) ?? null;
    return await SecureStore.getItemAsync(key);
  } catch {
    return null;
  }
}

export async function setPref(key: string, value: string): Promise<void> {
  try {
    if (Platform.OS === 'web') globalThis.localStorage?.setItem(key, value);
    else await SecureStore.setItemAsync(key, value);
  } catch {
    // A preference that can't be kept just isn't remembered.
  }
}
