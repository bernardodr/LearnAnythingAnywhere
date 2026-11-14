import { Redirect } from 'expo-router';
import { Platform, StyleSheet, Text } from 'react-native';

import { ComparePanel, ProvidersPanel, RoutingTable, RunLog, useOwner } from '@/components/Owner';
import { Button, goBack, Kicker, Note, Page } from '@/components/ui';
import { useSession } from '@/lib/session';
import { colors, type } from '@/theme';

/**
 * Screen 8: the owner console (web only, OWNER only). AI services and the default, per-task routing with
 * fallbacks, the run log, and "compare before switching". Changes apply to the next request; no restart.
 */
export default function OwnerConsole() {
  const { ready, user, token } = useSession();
  const owner = useOwner(user?.role === 'OWNER' ? token : null);

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;
  if (user.role !== 'OWNER') {
    return (
      <Page>
        <Text style={styles.title}>Owner console</Text>
        <Note>This page is for the people who run Learn Anything Anywhere.</Note>
        <Button label="‹ Your shelf" variant="quiet" onPress={goBack} style={styles.back} />
      </Page>
    );
  }
  if (Platform.OS !== 'web') {
    return (
      <Page>
        <Text style={styles.title}>Owner console</Text>
        <Note>The owner console works in a web browser. Open Learn Anything Anywhere on a computer to use it.</Note>
        <Button label="‹ Your shelf" variant="quiet" onPress={goBack} style={styles.back} />
      </Page>
    );
  }

  const { providers, routes, refresh } = owner;
  const failed = providers.error ?? routes.error;
  return (
    <Page wide>
      <Button label="‹ Your shelf" variant="quiet" onPress={goBack} style={styles.back} accessibilityLabel="Back to your shelf" />
      <Kicker>Learn Anything Anywhere</Kicker>
      <Text style={styles.title} accessibilityRole="header">
        Owner console
      </Text>
      <Text style={styles.intro}>Signed in as {user.email}. Every change here applies to the next request, for every learner.</Text>
      {failed ? <Note tone="error">{failed.message}</Note> : null}
      {providers.data && routes.data ? (
        <>
          <ProvidersPanel providers={providers.data} token={token} onChanged={refresh} />
          <RoutingTable routes={routes.data} providers={providers.data} token={token} onChanged={refresh} />
          <ComparePanel token={token} providers={providers.data} routes={routes.data} onChanged={refresh} />
          <RunLog token={token} providers={providers.data} />
        </>
      ) : null}
    </Page>
  );
}

const styles = StyleSheet.create({
  back: { alignSelf: 'flex-start', marginLeft: -8, marginBottom: 12 },
  title: { ...type.h1, marginTop: 8 },
  intro: { ...type.body, fontSize: 15, color: colors.muted, marginTop: 6 },
});
