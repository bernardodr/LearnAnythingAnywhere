import { useState } from 'react';
import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';

import { Diagram } from '@/components/Diagram';
import { Markdown } from '@/components/Markdown';
import { Kicker } from '@/components/ui';
import { type LessonContext, Practice, TutorChat } from '@/components/Tutor';
import type { Block, PracticeState, Source } from '@/lib/types';
import { colors, fonts, minTouch, themed, type } from '@/theme';

const RIGHT = '#2F6B3A';
const RIGHT_FILL = '#E5F1E6';
const WRONG_FILL = '#F8E5E1';

/** One block of a lesson (BRIEF section 7), in the textbook style of the example /teach lessons. */
export function LessonBlock({ block, sources, heading, ctx, practice }: { block: Block; sources: Source[]; heading?: string; ctx: LessonContext; practice?: PracticeState }) {
  switch (block.type) {
    case 'heading':
      return (
        <Text style={styles.h2} accessibilityRole="header">
          {block.text}
        </Text>
      );
    case 'text':
      return (
        <View style={styles.gap}>
          <Markdown md={block.md} />
          <Citations ids={block.citations} sources={sources} />
        </View>
      );
    case 'key_idea':
      return (
        <View style={styles.keyIdea}>
          <Kicker style={styles.boxLabel}>Key idea</Kicker>
          <Text style={styles.keyIdeaText}>{block.text}</Text>
        </View>
      );
    case 'callout':
      return (
        <View style={[styles.callout, block.kind === 'watch_out' && styles.watchOut]}>
          <Kicker style={styles.boxLabel}>
            {block.kind === 'watch_out' ? 'Watch out for this habit' : block.kind === 'win' ? 'Your win' : 'Note'}
          </Kicker>
          <Markdown md={block.text} size="small" />
        </View>
      );
    case 'quiz':
      return <Quiz block={block} heading={heading} />;
    case 'steps':
      return (
        <View style={styles.box}>
          <Kicker style={styles.boxLabel}>{block.title}</Kicker>
          {block.items.map((item, i) => (
            <View key={i} style={styles.step}>
              <Text style={styles.stepNo}>{i + 1}</Text>
              <Text style={styles.stepText}>{item}</Text>
            </View>
          ))}
        </View>
      );
    case 'free_response':
      return <Practice blockId={block.id} prompt={block.prompt} initial={practice} ctx={ctx} />;
    case 'primary_source': {
      const source = sources.find((s) => s.id === block.resource);
      if (!source) return null;
      return (
        <View style={styles.box}>
          <Kicker style={styles.boxLabel}>Primary source</Kicker>
          <SourceTitle source={source} />
          <Text style={styles.small}>{block.note}</Text>
        </View>
      );
    }
    case 'ask_tutor':
      return <TutorChat examples={block.examples} ctx={ctx} />;
    case 'diagram':
      return <Diagram block={block} />;
  }
}

function Quiz({ block, heading }: { block: Extract<Block, { type: 'quiz' }>; heading?: string }) {
  // The first choice counts (retrieval practice stays honest); the explanation shows either way.
  const [picked, setPicked] = useState<number | null>(null);
  const answered = picked !== null;
  const correct = picked === block.answer;
  return (
    <View style={styles.gap}>
      {heading ? (
        <Text style={styles.h2} accessibilityRole="header">
          {heading}
        </Text>
      ) : null}
      {heading && block.variant === 'warmup' ? (
        <Text style={styles.small}>Answer from memory. Recalling it is what makes it stick.</Text>
      ) : null}
      <View style={styles.quizCard}>
        <Text style={styles.question}>{block.question}</Text>
        {block.options.map((option, i) => {
          const isAnswer = i === block.answer;
          const state = !answered ? 'idle' : isAnswer ? 'right' : i === picked ? 'wrong' : 'idle';
          return (
            <Pressable
              key={i}
              accessibilityRole="button"
              accessibilityState={{ disabled: answered, selected: i === picked }}
              accessibilityLabel={option + (answered && isAnswer ? ', correct answer' : answered && i === picked ? ', your answer, not correct' : '')}
              disabled={answered}
              onPress={() => setPicked(i)}
              style={[styles.option, state === 'right' && styles.optionRight, state === 'wrong' && styles.optionWrong]}>
              <Text style={styles.optionText}>{option}</Text>
            </Pressable>
          );
        })}
        {answered ? (
          <View accessibilityLiveRegion="polite" style={styles.why}>
            <Text style={[styles.verdict, { color: correct ? RIGHT : colors.accent }]}>{correct ? 'Right.' : 'Not quite.'}</Text>
            <Text style={styles.small}>{block.explanation}</Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}

function Citations({ ids, sources }: { ids: string[]; sources: Source[] }) {
  const cited = ids.map((id) => sources.find((s) => s.id === id)).filter((s): s is Source => !!s);
  if (cited.length === 0) return null;
  return (
    <View style={styles.citations}>
      {cited.map((s) => (
        <Text key={s.id} style={styles.citation}>
          [{s.id.slice(1)}] <SourceTitle source={s} inline />
        </Text>
      ))}
    </View>
  );
}

/** "www.nasa.gov" from "https://www.nasa.gov/moon": the site a source is on, shown under it. */
function siteOf(url: string) {
  const m = /^https?:\/\/([^/?#]+)/i.exec(url);
  return m ? m[1] : url;
}

/**
 * A lesson's sources, at its end like the references after a book's chapter (issue #82): each numbered as the lesson
 * cites it, with its title (a link if the reader's links setting allows), what it's good for, and the site it's on.
 * These are the ones found for this lesson by web search and checked by LAA, and any the lesson cites (the learner's
 * own included). With none, it says so and points to the back of the book.
 *
 * @param sources the lesson's own sources (`LessonView.sources`)
 */
export function LessonSources({ sources }: { sources: Source[] }) {
  return (
    <View style={styles.sources}>
      <Kicker style={styles.sourcesLabel}>Sources</Kicker>
      {sources.length === 0 ? (
        <Text style={styles.sourcesNone}>
          No sources could be checked for this lesson. You can add ones you trust at the back of the book, and later
          lessons can cite them.
        </Text>
      ) : (
        sources.map((s) => (
          <View key={s.id} style={styles.sourceRow} accessible accessibilityLabel={`Source ${s.id.slice(1)}: ${s.title}. ${s.note}`}>
            <Text style={styles.sourceNo}>{s.id.slice(1)}</Text>
            <View style={styles.sourceBody}>
              <SourceTitle source={s} />
              {s.note ? <Text style={styles.sourceNote}>{s.note}</Text> : null}
              {s.url ? <Text style={styles.sourceSite}>{siteOf(s.url)}</Text> : null}
            </View>
          </View>
        ))
      )}
      <Text style={styles.sourcesFoot}>
        Every source is a real page: found by a web search and checked by LAA, or added by you. LAA never makes them up.
      </Text>
    </View>
  );
}

export function SourceTitle({ source, inline }: { source: Source; inline?: boolean }) {
  const style = inline ? styles.citationLink : styles.sourceTitle;
  if (!source.url) return <Text style={style}>{source.title}</Text>;
  return (
    <Text style={[style, styles.link]} accessibilityRole="link" onPress={() => void Linking.openURL(source.url!)}>
      {source.title}
    </Text>
  );
}

const styles = themed(() => StyleSheet.create({
  gap: { marginBottom: 8 },
  // The sources at a lesson's end: a fine rule above, then numbered entries like a book's references.
  sources: { marginTop: 32, paddingTop: 20, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.rule },
  sourcesLabel: { color: colors.ink, marginBottom: 12 },
  sourcesNone: { ...type.meta, fontSize: 14, lineHeight: 20, marginBottom: 12 },
  sourceRow: { flexDirection: 'row', gap: 12, marginBottom: 16 },
  sourceNo: { fontFamily: fonts.serifSemiBold, fontSize: 16, lineHeight: 26, color: colors.accent, width: 20 },
  sourceBody: { flex: 1 },
  sourceNote: { ...type.small, fontSize: 15, lineHeight: 22 },
  sourceSite: { ...type.meta, marginTop: 4 },
  sourcesFoot: { ...type.meta, marginTop: 4 },
  h2: { ...type.h2, marginTop: 20, marginBottom: 10 },
  small: { ...type.body, fontSize: 15, lineHeight: 15 * 1.5 },
  box: { borderWidth: 1, borderColor: colors.rule, borderRadius: 4, padding: 16, marginVertical: 12, backgroundColor: colors.field },
  boxLabel: { marginBottom: 8 },
  keyIdea: { borderLeftWidth: 3, borderLeftColor: colors.accent, paddingLeft: 16, paddingVertical: 4, marginVertical: 16 },
  keyIdeaText: { ...type.body, fontFamily: fonts.serifSemiBold, fontSize: 19, lineHeight: 19 * 1.45 },
  callout: { backgroundColor: colors.noteFill, borderRadius: 4, padding: 16, marginVertical: 12 },
  watchOut: { borderLeftWidth: 3, borderLeftColor: colors.accent },
  step: { flexDirection: 'row', marginBottom: 8 },
  stepNo: { ...type.body, fontFamily: fonts.serifSemiBold, width: 24, color: colors.accent },
  stepText: { ...type.body, flex: 1, fontSize: 16, lineHeight: 16 * 1.5 },
  quizCard: { borderWidth: 1, borderColor: colors.rule, borderRadius: 6, padding: 16, backgroundColor: colors.field, marginVertical: 8 },
  question: { fontFamily: fonts.sansSemiBold, fontSize: 16, lineHeight: 22, color: colors.ink, marginBottom: 12 },
  option: {
    minHeight: minTouch,
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.rule,
    borderRadius: 4,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginBottom: 8,
    backgroundColor: colors.paper,
  },
  optionRight: { backgroundColor: RIGHT_FILL, borderColor: RIGHT },
  optionWrong: { backgroundColor: WRONG_FILL, borderColor: colors.accent },
  optionText: { fontFamily: fonts.sans, fontSize: 15, lineHeight: 21, color: colors.ink },
  why: { marginTop: 6 },
  verdict: { fontFamily: fonts.sansSemiBold, fontSize: 14, marginBottom: 4 },
  example: { ...type.body, fontSize: 15, fontFamily: fonts.serifItalic, marginTop: 6 },
  soon: { fontFamily: fonts.sans, fontSize: 13, lineHeight: 19, color: colors.muted, marginTop: 10 },
  citations: { marginTop: -4, marginBottom: 8 },
  citation: { fontFamily: fonts.sans, fontSize: 12, color: colors.muted },
  citationLink: { fontFamily: fonts.sans, fontSize: 12 },
  sourceTitle: { ...type.body, fontFamily: fonts.serifSemiBold, marginBottom: 4 },
  link: { color: colors.accent, textDecorationLine: 'underline' },
}));
