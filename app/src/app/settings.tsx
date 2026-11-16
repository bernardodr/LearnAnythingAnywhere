import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';

import { readingLevel, SettingsForm, SettingsSummary, WEB_SWITCH } from '@/components/Settings';
import { Button, Field, goBack, Kicker, Note, Ornament, Page, PasswordField, passwordLength } from '@/components/ui';
import { ApiError, apiGet, apiPost, apiPut } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { Child, ParentState, Profile, ProfileSettings, SignInResponse } from '@/lib/types';
import { colors, fonts, minTouch, type } from '@/theme';

/**
 * Screen 7: reader settings. A learner's own settings (a child sees theirs, managed by their parent); for a parent,
 * their children with each child's settings and password, adding a child, and the parent PIN that locks them; and
 * everyone's own password.
 */

/** The server's rule; it says so if a password breaks it. */
const MIN_PASSWORD = 15;
export default function SettingsScreen() {
  const { ready, user, token } = useSession();
  const queryClient = useQueryClient();
  const me = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => apiGet<Profile>('/profiles/me', token),
    enabled: !!token,
  });
  const isParent = user?.role === 'PARENT';
  const [savedMine, setSavedMine] = useState(false);
  const family = useQuery({
    queryKey: ['profile', 'children'],
    queryFn: () => apiGet<ParentState>('/profiles/children', token),
    enabled: !!token && isParent,
  });

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;

  // Optional chaining, not `!`: React Compiler reads this closure's dependencies while the profile is still loading.
  const myProfileId = me.data?.id;
  const saveMine = async (s: ProfileSettings) => {
    const saved = await apiPut<Profile>(`/profiles/${myProfileId}`, s, token);
    queryClient.setQueryData(['profile', 'me'], saved);
    setSavedMine(true);
  };

  return (
    <Page>
      <View style={styles.header}>
        <Button label="‹ Your shelf" variant="quiet" onPress={goBack} accessibilityLabel="Back to your shelf" />
      </View>
      <Kicker>Reader settings</Kicker>
      <Text style={styles.title} accessibilityRole="header">
        How your textbooks are written
      </Text>
      <Text style={styles.intro}>
        These settings shape every lesson, every tutor reply and every check, whichever AI service is writing them.
      </Text>

      {me.isError ? <Note tone="error">{me.error.message}</Note> : null}
      {me.data ? (
        me.data.canEdit ? (
          <SettingsForm key={JSON.stringify(me.data)} profile={me.data} forChild={false} needsPin={false} saved={savedMine}
            onEdit={() => setSavedMine(false)} onSave={saveMine} />
        ) : (
          <View>
            <Note>Your parent manages these settings. Ask them if something should change.</Note>
            <SettingsSummary profile={me.data} />
          </View>
        )
      ) : null}

      {isParent ? (
        <View>
          <Ornament />
          <Text style={styles.h2} accessibilityRole="header">
            Your children
          </Text>
          {family.isError ? <Note tone="error">{family.error.message}</Note> : null}
          {family.data?.children.length === 0 ? (
            <Text style={styles.intro}>Add a child to give them their own shelf, with settings you choose.</Text>
          ) : null}
          {family.data?.children.map((c) => (
            <ChildCard key={c.userId} child={c} token={token} pinSet={family.data?.pinSet ?? false}
              onSaved={() => queryClient.invalidateQueries({ queryKey: ['profile', 'children'] })} />
          ))}
          <AddChild token={token} onAdded={() => queryClient.invalidateQueries({ queryKey: ['profile', 'children'] })} />
          {family.data ? (
            <ParentPin key={String(family.data.pinSet)} token={token} pinSet={family.data.pinSet}
              onSet={() => queryClient.invalidateQueries({ queryKey: ['profile', 'children'] })} />
          ) : null}
        </View>
      ) : null}

      <Ornament />
      <Text style={styles.h2} accessibilityRole="header">
        Your password
      </Text>
      <YourPassword />
    </Page>
  );
}

function ChildCard({ child, token, pinSet, onSaved }: { child: Child; token: string | null; pinSet: boolean; onSaved: () => void }) {
  const [open, setOpen] = useState(false);
  const [saved, setSaved] = useState(false);
  const p = child.profile;
  const profileId = p?.id;
  const name = child.displayName ?? child.email;
  return (
    <View style={styles.card}>
      <Pressable accessibilityRole="button" accessibilityState={{ expanded: open }}
        accessibilityLabel={`${open ? 'Hide' : 'Change'} settings for ${name}`} onPress={() => setOpen(!open)} style={styles.cardHead}>
        <View style={{ flex: 1 }}>
          <Text style={styles.cardTitle}>{name}</Text>
          <Text style={styles.small}>
            {child.email}
            {p ? ` · ${p.readingLevel}${p.childSafe ? ' · child-safe' : ''}` : ''}
          </Text>
        </View>
        <Text style={styles.cardToggle}>{open ? 'Hide ▴' : 'Change ▾'}</Text>
      </Pressable>
      {open && p ? (
        <SettingsForm key={JSON.stringify(p)} profile={p} forChild needsPin={pinSet} saved={saved} onEdit={() => setSaved(false)}
          onSave={async (s, pin) => {
            await apiPut<Profile>(`/profiles/${profileId}`, s, token, pin ? { 'X-Parent-Pin': pin } : undefined);
            setSaved(true);
            onSaved();
          }} />
      ) : null}
      {open ? <ChildPassword child={child} name={name} token={token} pinSet={pinSet} /> : null}
    </View>
  );
}

/** A parent sets a new password for their child; the child's other sign-ins end. */
function ChildPassword({ child, name, token, pinSet }: { child: Child; name: string; token: string | null; pinSet: boolean }) {
  const [password, setPassword] = useState('');
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);

  async function save() {
    setBusy(true);
    setStatus(null);
    try {
      await apiPut(`/profiles/children/${child.userId}/password`, { password }, token, pinSet ? { 'X-Parent-Pin': pin } : undefined);
      setStatus({ ok: true, text: `Password changed. ${name} will need to sign in again with the new one.` });
      setPassword('');
      setPin('');
    } catch (e) {
      setStatus({ ok: false, text: e instanceof ApiError ? e.message : 'The password couldn’t be changed. Please try again.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.subsection}>
      <PasswordField label={`New password for ${name}`} isNew value={password} onChangeText={(t) => { setPassword(t); setStatus(null); }}
        hint={`At least ${MIN_PASSWORD} characters. Three or four random words work well.`} />
      {pinSet ? (
        <Field label="Parent PIN" value={pin} onChangeText={(t) => setPin(t.replace(/\D/g, ''))} secureTextEntry inputMode="numeric"
          keyboardType="number-pad" maxLength={8} hint="Changing a child’s password needs your PIN." />
      ) : null}
      {status ? <Note tone={status.ok ? 'note' : 'error'}>{status.text}</Note> : null}
      <Button label="Change password" variant="secondary" onPress={() => void save()} busy={busy}
        disabled={passwordLength(password) < MIN_PASSWORD || (pinSet && pin.length < 4)} style={styles.pinButton} />
    </View>
  );
}

/** Your own password: change it (with the current one), or set one if the account has none yet. */
function YourPassword() {
  const { token, user, renew } = useSession();
  const hasPassword = user?.hasPassword ?? false;
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);

  async function save() {
    setBusy(true);
    setStatus(null);
    try {
      const res = await apiPut<SignInResponse>('/auth/password', { currentPassword: hasPassword ? current : null, newPassword: next }, token);
      await renew(res);
      setStatus({ ok: true, text: 'Password saved. You’re still signed in here; anywhere else you were signed in will ask you to sign in again.' });
      setCurrent('');
      setNext('');
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
      {status ? <Note tone={status.ok ? 'note' : 'error'}>{status.text}</Note> : null}
      <Button label={hasPassword ? 'Change password' : 'Set password'} variant="secondary" onPress={() => void save()} busy={busy}
        disabled={passwordLength(next) < MIN_PASSWORD || (hasPassword && current.length === 0)} style={styles.pinButton} />
    </View>
  );
}

function AddChild({ token, onAdded }: { token: string | null; onAdded: () => void }) {
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [age, setAge] = useState(8);
  const [childSafe, setChildSafe] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) return <Button label="Add a child" variant="secondary" onPress={() => setOpen(true)} style={styles.addChild} />;

  async function add() {
    setBusy(true);
    setError(null);
    try {
      await apiPost('/profiles/children', { email: email.trim(), displayName: name.trim() || null, age, childSafe, password }, token);
      setOpen(false);
      setEmail('');
      setPassword('');
      setName('');
      onAdded();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'The child couldn’t be added. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>Add a child</Text>
      <Text style={styles.small}>
        You’re making your child’s account: they sign in with this email and the password you choose, and see only their own
        shelf.
      </Text>
      <View style={{ height: 12 }} />
      <Field label="Their name" value={name} onChangeText={setName} maxLength={80} />
      <Field label="Their sign-in email" value={email} onChangeText={setEmail} autoCapitalize="none" autoCorrect={false}
        inputMode="email" keyboardType="email-address" maxLength={254} />
      <PasswordField label="Their password" isNew value={password} onChangeText={setPassword}
        hint={`At least ${MIN_PASSWORD} characters (${passwordLength(password)} so far). Three or four random words are easy for a child to type.`} />
      <Text style={styles.label}>Age</Text>
      <View style={styles.ageRow}>
        <Button label="−" variant="secondary" accessibilityLabel="New child’s age: younger" disabled={age <= 5} onPress={() => setAge(age - 1)} />
        <Text style={styles.ageText} accessibilityLiveRegion="polite">
          {age} · {readingLevel(age)}
        </Text>
        <Button label="+" variant="secondary" accessibilityLabel="New child’s age: older" disabled={age >= 18} onPress={() => setAge(age + 1)} />
      </View>
      <View style={styles.toggle}>
        <Text style={styles.toggleLabel}>Child-safe mode</Text>
        <Switch value={childSafe} onValueChange={setChildSafe} accessibilityLabel="Child-safe mode for the new child"
          trackColor={{ true: colors.accent, false: colors.control }} thumbColor={colors.paper} {...WEB_SWITCH} />
      </View>
      {error ? <Note tone="error">{error}</Note> : null}
      <View style={styles.actions}>
        <Button label="Add child" onPress={() => void add()} busy={busy}
          disabled={!email.includes('@') || passwordLength(password) < MIN_PASSWORD} />
        <Button label="Cancel" variant="quiet" onPress={() => setOpen(false)} />
      </View>
    </View>
  );
}

function ParentPin({ token, pinSet, onSet }: { token: string | null; pinSet: boolean; onSet: () => void }) {
  const [pin, setPin] = useState('');
  const [current, setCurrent] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);
  const digits = (t: string) => t.replace(/\D/g, '');

  async function save() {
    setBusy(true);
    setStatus(null);
    try {
      await apiPut('/profiles/pin', { pin, currentPin: pinSet ? current : null }, token);
      setStatus({ ok: true, text: 'PIN saved. Changes to your children’s settings now need it.' });
      setPin('');
      setCurrent('');
      onSet();
    } catch (e) {
      setStatus({ ok: false, text: e instanceof ApiError ? e.message : 'The PIN couldn’t be saved. Please try again.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>Parent PIN lock</Text>
      <Text style={styles.small}>
        {pinSet
          ? 'Your PIN is set: changing a child’s settings asks for it.'
          : 'Set a PIN so only you can change your children’s settings, even on a shared device.'}
      </Text>
      <View style={{ height: 12 }} />
      {pinSet ? (
        <Field label="Current PIN" value={current} onChangeText={(t) => setCurrent(digits(t))} secureTextEntry inputMode="numeric"
          keyboardType="number-pad" maxLength={8} />
      ) : null}
      <Field label={pinSet ? 'New PIN' : 'PIN'} value={pin} onChangeText={(t) => setPin(digits(t))} secureTextEntry inputMode="numeric"
        keyboardType="number-pad" maxLength={8} hint="4 to 8 digits." />
      {status ? <Note tone={status.ok ? 'note' : 'error'}>{status.text}</Note> : null}
      <Button label={pinSet ? 'Change PIN' : 'Set PIN'} variant="secondary" onPress={() => void save()} busy={busy}
        disabled={pin.length < 4 || (pinSet && current.length < 4)} style={styles.pinButton} />
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', marginBottom: 8, marginLeft: -8 },
  title: { ...type.h1, marginTop: 8, marginBottom: 12 },
  h2: { ...type.h2, marginBottom: 12 },
  intro: { ...type.body, color: colors.muted, marginBottom: 16 },
  small: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 18, color: colors.muted },
  label: { ...type.kicker, marginBottom: 8 },
  card: { borderWidth: 1, borderColor: colors.rule, borderRadius: 4, padding: 16, marginBottom: 14, backgroundColor: '#FFFFFF' },
  cardHead: { minHeight: minTouch, flexDirection: 'row', alignItems: 'center', gap: 12 },
  cardTitle: { fontFamily: fonts.serifSemiBold, fontSize: 19, color: colors.ink },
  cardToggle: { fontFamily: fonts.sansSemiBold, fontSize: 14, color: colors.accent },
  addChild: { alignSelf: 'flex-start', marginBottom: 14 },
  ageRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 12 },
  ageText: { ...type.body, flex: 1, textAlign: 'center' },
  toggle: { minHeight: minTouch, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 },
  toggleLabel: { fontFamily: fonts.sans, fontSize: 16, color: colors.ink },
  actions: { flexDirection: 'row', gap: 12, alignItems: 'center' },
  pinButton: { alignSelf: 'flex-start' },
  subsection: { borderTopWidth: 1, borderTopColor: colors.rule, marginTop: 16, paddingTop: 16 },
});
