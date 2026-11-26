import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useColorScheme } from 'react-native';

import { getPref, setPref } from '@/lib/prefs';
import type { Scheme } from '@/theme';

/** The learner's choice in settings: follow the device, or always light, or always dark (issue #85). */
export type Appearance = 'system' | 'light' | 'dark';

const KEY = 'laa.appearance';

/**
 * The appearance choice, kept on this device (like covers or spines): `appearance` is what's chosen (System until
 * changed), `scheme` the palette that means now (System follows the device's light or dark setting), and
 * `choose` changes it, saved straight away. The root layout reads `scheme`; settings offers the choice.
 */
export function useAppearance() {
  const queryClient = useQueryClient();
  const device = useColorScheme();
  const saved = useQuery({
    queryKey: ['pref', 'appearance'],
    queryFn: async (): Promise<Appearance> => {
      const v = await getPref(KEY);
      return v === 'light' || v === 'dark' ? v : 'system';
    },
    staleTime: Infinity,
  });
  const appearance: Appearance = saved.data ?? 'system';
  const scheme: Scheme = appearance === 'system' ? (device === 'dark' ? 'dark' : 'light') : appearance;
  const choose = (next: Appearance) => {
    queryClient.setQueryData(['pref', 'appearance'], next);
    void setPref(KEY, next);
  };
  return { appearance, scheme, ready: saved.isFetched, choose };
}
