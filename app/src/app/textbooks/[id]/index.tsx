import { useQuery } from '@tanstack/react-query';
import { Redirect, useLocalSearchParams } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { Button, goBack, Kicker, Note, Ornament, Page } from '@/components/ui';
import { apiGet } from '@/lib/api';
import { useSession } from '@/lib/session';
import type { MissionState } from '@/lib/types';
import { colors, fonts, type } from '@/theme';

/**
 * Screen 4, the contents page. M2 shows the title and the confirmed mission as its epigraph; the lesson list
 * and the back of the book arrive with lessons in M3.
 */
export default function Contents() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { ready, user, token } = useSession();
  const mission = useQuery({
    queryKey: ['mission', id],
    queryFn: () => apiGet<MissionState>(`/textbooks/${id}/mission`, token),
    enabled: !!token && !!id,
  });
  const file = useQuery({
    queryKey: ['file', id, 'MISSION.md'],
    queryFn: () => apiGet<{ content: string; version: number }>(`/textbooks/${id}/files/MISSION.md`, token),
    enabled: !!token && !!id && mission.data?.status === 'ACTIVE',
  });

  if (!ready) return null;
  if (!user) return <Redirect href="/" />;
  if (mission.data?.status === 'INTERVIEW') {
    return <Redirect href={{ pathname: '/textbooks/[id]/preface', params: { id } }} />;
  }
  const draft = mission.data?.draft;

  return (
    <Page>
      <Button label="‹ Your shelf" variant="quiet" onPress={goBack} style={styles.back}
        accessibilityLabel="Back to your shelf" />
      {mission.isError ? <Note tone="error">{mission.error.message}</Note> : null}
      <Text style={styles.title} accessibilityRole="header">
        {mission.data?.title ?? ' '}
      </Text>
      {draft ? (
        <View style={styles.epigraph}>
          <Text style={styles.epigraphText}>{draft.why}</Text>
          <Text style={styles.epigraphBy}>— your mission: {draft.headline}</Text>
        </View>
      ) : null}
      <Ornament />
      <Kicker>Contents</Kicker>
      <View style={styles.entry}>
        <Text style={styles.entryNo}>1</Text>
        <Text style={styles.entryTitle}>Your first lesson</Text>
        <View style={styles.leader} />
        <Text style={styles.entryStatus}>Written next</Text>
      </View>
      <Note>Lessons are written in the next milestone. Your mission is saved and will shape every one of them.</Note>

      {file.data ? (
        <View style={styles.file}>
          <Kicker style={styles.fileLabel}>MISSION.md · version {file.data.version}</Kicker>
          <Text style={styles.fileText} selectable>
            {file.data.content}
          </Text>
        </View>
      ) : null}
    </Page>
  );
}

const styles = StyleSheet.create({
  back: { alignSelf: 'flex-start', marginLeft: -8, marginBottom: 12 },
  title: { ...type.h1, textAlign: 'center', marginTop: 8 },
  epigraph: { marginTop: 20, paddingHorizontal: 12 },
  epigraphText: { ...type.body, fontFamily: fonts.serifItalic, textAlign: 'center' },
  epigraphBy: { fontFamily: fonts.sans, fontSize: 13, color: colors.muted, textAlign: 'center', marginTop: 8 },
  entry: { flexDirection: 'row', alignItems: 'flex-end', marginTop: 12, marginBottom: 8 },
  entryNo: { ...type.body, width: 28, color: colors.accent },
  entryTitle: { ...type.body },
  leader: { flex: 1, borderBottomWidth: 1, borderStyle: 'dotted', borderColor: colors.muted, marginHorizontal: 8, marginBottom: 7 },
  entryStatus: { ...type.kicker, fontSize: 11, marginBottom: 4 },
  file: { marginTop: 24, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.rule, paddingTop: 16 },
  fileLabel: { marginBottom: 8 },
  fileText: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 20, color: colors.ink },
});
