import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { DiagramFrame } from '@/components/DiagramFrame';
import { Markdown } from '@/components/Markdown';
import { Button, Kicker } from '@/components/ui';
import type { Block } from '@/lib/types';
import { colors, fonts, type } from '@/theme';

type DiagramBlock = Extract<Block, { type: 'diagram' }>;

/**
 * An interactive diagram in a lesson (a `diagram` block): its title, the diagram itself in a sealed frame
 * (`DiagramFrame`), and the caption saying what to try.
 *
 * Every diagram also has a text version, written with it and checked with it. The learner can switch to it at any
 * time with *Show as text*, which helps anyone who'd rather read, or who uses a screen reader. The app switches to it
 * by itself if the diagram's page fails on this device, with a note saying so. So the lesson never shows a broken
 * picture.
 */
export function Diagram({ block }: { block: DiagramBlock }) {
  const [asText, setAsText] = useState(false);
  const [failed, setFailed] = useState(false);
  // Stable, so the web frame's message listener isn't re-attached on every render.
  const onFailed = useCallback(() => setFailed(true), []);
  const showText = asText || failed;

  return (
    <View style={styles.figure}>
      <Kicker style={styles.label}>Diagram</Kicker>
      <Text style={styles.title} accessibilityRole="header">
        {block.title}
      </Text>
      {showText ? (
        <View style={styles.textVersion}>
          {failed ? <Text style={styles.note}>This diagram couldn’t be shown here, so here it is in words.</Text> : null}
          <Markdown md={block.text} size="small" />
        </View>
      ) : (
        <DiagramFrame html={block.html} title={block.title} onFailed={onFailed} />
      )}
      <Text style={styles.caption}>{block.caption}</Text>
      {failed ? null : (
        <Button
          label={asText ? 'Show the diagram' : 'Show as text'}
          variant="quiet"
          onPress={() => setAsText((v) => !v)}
          accessibilityLabel={asText ? `Show the diagram: ${block.title}` : `Show ${block.title} as text`}
          style={styles.toggle}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  figure: { borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.rule, paddingVertical: 14, marginVertical: 16 },
  label: { marginBottom: 4 },
  title: { ...type.body, fontFamily: fonts.serifSemiBold, fontSize: 18, marginBottom: 10 },
  textVersion: { backgroundColor: colors.noteFill, borderRadius: 4, padding: 14 },
  note: { ...type.body, fontSize: 15, fontFamily: fonts.serifItalic, color: colors.muted, marginBottom: 8 },
  caption: { ...type.body, fontSize: 15, lineHeight: 15 * 1.5, fontFamily: fonts.serifItalic, color: colors.muted, marginTop: 10 },
  toggle: { alignSelf: 'flex-start', marginTop: 4 },
});
