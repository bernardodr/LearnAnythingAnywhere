import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, router } from 'expo-router';
import { useState } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';

import { SettingsForm } from '@/components/Settings';
import { Button, Divider, Field, goBack, IconButton, Kicker, Note, Ornament, Page, PasswordField, passwordLength } from '@/components/ui';
import { ApiError, apiGet, apiPost, apiPut, api } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { Profile, ProfileSettings, SignInResponse } from '@/lib/types';
import { colors, fonts, type } from '@/theme';

/** The server's rule; it says so if a password breaks it. */
const MIN_PASSWORD = 15;

const digits = (t: string) => t.replace(/\D/g, '');

/**
 * Screen 7: reader settings. One account, one set of settings: an adult's own, or set up for a child who uses the
 * account. The parent PIN locks the settings and the password, so a child can't change them; a forgotten PIN is
 * replaced with the account password.
 */
export default function SettingsScreen() {
  const { ready, user, token, signOut } = useSession();
  const queryClient = useQueryClient();
  const me = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => apiGet<Profile>('/profiles/me', token),
    enabled: !!token,
  });
  const [saved, setSaved] = useState(false);

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;

  // Optional chaining, not `!`: React Compiler reads this closure's dependencies while the profile is still loading.
  const profileId = me.data?.id;
  const save = async (s: ProfileSettings, pin: string | null) => {
    const next = await apiPut<Profile>(`/profiles/${profileId}`, s, token, pin ? { 'X-Parent-Pin': pin } : undefined);
    queryClient.setQueryData(['profile', 'me'], next);
    setSaved(true);
  };
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['profile', 'me'] });
  const pinSet = me.data?.pinSet ?? false;

  return (
    <Page>
      <View style={styles.header}>
        <IconButton icon="back" label="Back to your library" onPress={goBack} />
      </View>
      <Kicker>Reader settings</Kicker>
      <Text style={styles.title} accessibilityRole="header">
        How your textbooks are written
      </Text>
      <Text style={styles.intro}>
        These settings shape every lesson, every tutor reply and every check, whichever AI service is writing them. If
        a child uses this account, set them for the child, then lock them with a parent PIN.
      </Text>

      {me.isError ? <Note tone="error">{me.error.message}</Note> : null}
      {me.data ? (
        <SettingsForm key={JSON.stringify(me.data)} profile={me.data} saved={saved} onEdit={() => setSaved(false)} onSave={save} />
      ) : null}

      <Ornament />
      <Text style={styles.h2} accessibilityRole="header">
        Parent PIN
      </Text>
      {me.data ? <ParentPin pinSet={pinSet} onChange={refresh} /> : null}

      <Ornament />
      <Text style={styles.h2} accessibilityRole="header">
        Your password
      </Text>
      <YourPassword pinSet={pinSet} />

      {/* The account: signing out, and for the owner on the web, the owner console. (They lived in the library's
          header before the facelift, which gives the library a single settings icon instead.) */}
      <Divider style={styles.accountRule} />
      <Text style={styles.signedIn}>Signed in as {user.email}</Text>
      <View style={styles.actions}>
        {user.role === 'OWNER' && Platform.OS === 'web' ? (
          <Button label="Owner console" variant="secondary" onPress={() => router.push('/owner')} />
        ) : null}
        <Button label="Sign out" variant="quiet" onPress={() => void signOut().then(() => router.replace('/'))}
          accessibilityLabel={`Sign out ${user.email}`} />
      </View>
    </Page>
  );
}

/** Set, change or turn off the parent PIN; a forgotten one is replaced with the account password. */
function ParentPin({ pinSet, onChange }: { pinSet: boolean; onChange: () => void }) {
  const { token } = useSession();
  const [pin, setPin] = useState('');
  const [current, setCurrent] = useState('');
  const [forgot, setForgot] = useState(false);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);

  async function run(work: () => Promise<unknown>, done: string) {
    setBusy(true);
    setStatus(null);
    try {
      await work();
      setStatus({ ok: true, text: done });
      setPin('');
      setCurrent('');
      setPassword('');
      onChange();
    } catch (e) {
      setStatus({ ok: false, text: e instanceof ApiError ? e.message : 'That didn’t work. Please try again.' });
    } finally {
      setBusy(false);
    }
  }

  const save = () =>
    run(() => apiPut('/profiles/pin', { pin, currentPin: pinSet ? current : null }, token),
      'PIN saved. Changing the reader settings or the password now needs it.');
  const turnOff = () =>
    run(() => api('DELETE', '/profiles/pin', { token, headers: { 'X-Parent-Pin': current } }), 'The lock is off.');
  const reset = () =>
    run(() => apiPost('/profiles/pin/reset', { password, pin }, token), 'New PIN saved.');

  return (
    <View style={styles.card}>
      <Text style={styles.small}>
        {pinSet
          ? 'The PIN is set: changing the reader settings or the password asks for it. Reading, practice and the tutor stay open.'
          : 'Letting a child use this account? Set a PIN so only you can change the reader settings and the password.'}
      </Text>
      <View style={{ height: 12 }} />
      {pinSet && !forgot ? (
        <Field label="Current PIN" value={current} onChangeText={(t) => setCurrent(digits(t))} secureTextEntry inputMode="numeric"
          keyboardType="number-pad" maxLength={8} />
      ) : null}
      {forgot ? (
        <PasswordField label="Your account password" value={password} onChangeText={setPassword}
          hint="Forgotten the PIN? Your password lets you choose a new one." />
      ) : null}
      <Field label={pinSet ? 'New PIN' : 'PIN'} value={pin} onChangeText={(t) => setPin(digits(t))} secureTextEntry inputMode="numeric"
        keyboardType="number-pad" maxLength={8} hint="4 to 8 digits." />
      {status ? <Note tone={status.ok ? 'note' : 'error'}>{status.text}</Note> : null}
      <View style={styles.actions}>
        {forgot ? (
          <Button label="Save new PIN" variant="secondary" onPress={() => void reset()} busy={busy}
            disabled={pin.length < 4 || password.length === 0} />
        ) : (
          <Button label={pinSet ? 'Change PIN' : 'Set PIN'} variant="secondary" onPress={() => void save()} busy={busy}
            disabled={pin.length < 4 || (pinSet && current.length < 4)} />
        )}
        {pinSet && !forgot ? (
          <Button label="Turn the lock off" variant="quiet" onPress={() => void turnOff()} disabled={busy || current.length < 4} />
        ) : null}
      </View>
      {pinSet ? (
        <Button label={forgot ? 'I remember the PIN' : 'Forgotten the PIN?'} variant="quiet"
          onPress={() => { setForgot(!forgot); setStatus(null); }} style={styles.forgot} />
      ) : null}
    </View>
  );
}

/** Your own password: change it (with the current one, and the parent PIN once set), or set one if there's none. */
function YourPassword({ pinSet }: { pinSet: boolean }) {
  const { token, user, renew } = useSession();
  const hasPassword = user?.hasPassword ?? false;
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);

  async function save() {
    setBusy(true);
    setStatus(null);
    try {
      const res = await apiPut<SignInResponse>('/auth/password',
        { currentPassword: hasPassword ? current : null, newPassword: next, pin: pinSet ? pin : null }, token);
      await renew(res);
      setStatus({ ok: true, text: 'Password saved. You’re still signed in here; anywhere else you were signed in will ask you to sign in again.' });
      setCurrent('');
      setNext('');
      setPin('');
    } catch (e) {
      setStatus({ ok: false, text: e instanceof ApiError ? e.message : 'The password couldn’t be saved. Please try again.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.small}>
        {hasPassword
          ? 'Change the password you sign in with. Changing it signs you out everywhere else.'
          : 'Your account doesn’t have a password yet. Set one so you can sign in with your email and password.'}
      </Text>
      <View style={{ height: 12 }} />
      {hasPassword ? <PasswordField label="Current password" value={current} onChangeText={setCurrent} /> : null}
      <PasswordField label={hasPassword ? 'New password' : 'Password'} isNew value={next} onChangeText={setNext}
        hint={`At least ${MIN_PASSWORD} characters (${passwordLength(next)} so far). Three or four random words work well, and spaces are fine.`} />
      {pinSet ? (
        <Field label="Parent PIN for the password" value={pin} onChangeText={(t) => setPin(digits(t))} secureTextEntry inputMode="numeric"
          keyboardType="number-pad" maxLength={8} hint="Changing the password needs the parent PIN." />
      ) : null}
      {status ? <Note tone={status.ok ? 'note' : 'error'}>{status.text}</Note> : null}
      <Button label={hasPassword ? 'Change password' : 'Set password'} variant="secondary" onPress={() => void save()} busy={busy}
        disabled={passwordLength(next) < MIN_PASSWORD || (hasPassword && current.length === 0) || (pinSet && pin.length < 4)}
        style={styles.button} />
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', marginBottom: 8, marginLeft: -8 },
  title: { ...type.h1, marginTop: 8, marginBottom: 12 },
  h2: { ...type.h2, marginBottom: 12 },
  intro: { ...type.body, color: colors.muted, marginBottom: 16 },
  small: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 18, color: colors.muted },
  card: { borderWidth: 1, borderColor: colors.rule, borderRadius: 4, padding: 16, marginBottom: 14, backgroundColor: colors.field },
  actions: { flexDirection: 'row', gap: 12, alignItems: 'center', flexWrap: 'wrap' },
  forgot: { alignSelf: 'flex-start', marginTop: 8, marginLeft: -8 },
  button: { alignSelf: 'flex-start' },
  accountRule: { marginTop: 40, marginBottom: 20 },
  signedIn: { ...type.meta, marginBottom: 8 },
});
