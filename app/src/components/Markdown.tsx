import { Fragment, type ReactNode } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';

import { colors, fonts, type } from '@/theme';

/**
 * Renders the small Markdown subset lessons are written in (see prompts/lesson_write.v1.md): paragraphs,
 * **bold**, *italic*, `code`, bullet and numbered lists, and fenced code blocks. Anything else shows as plain
 * text. Links show their text only: lessons cite sources through ids, never inline URLs.
 */
export function Markdown({ md, size = 'body' }: { md: string; size?: 'body' | 'small' }) {
  const base = size === 'small' ? styles.small : styles.body;
  return (
    <View>
      {parseBlocks(md).map((block, i) => {
        switch (block.kind) {
          case 'code':
            return (
              <View key={i} style={styles.codeBox}>
                <Text style={styles.code} selectable>
                  {block.lines.join('\n')}
                </Text>
              </View>
            );
          case 'list':
            return (
              <View key={i} style={styles.list}>
                {block.lines.map((item, j) => (
                  <View key={j} style={styles.item}>
                    <Text style={[base, styles.marker]}>{block.ordered ? `${j + 1}.` : '•'}</Text>
                    <Text style={[base, styles.itemText]}>{inline(item)}</Text>
                  </View>
                ))}
              </View>
            );
          default:
            return (
              <Text key={i} style={[base, styles.paragraph]}>
                {inline(block.lines.join(' '))}
              </Text>
            );
        }
      })}
    </View>
  );
}

type Block = { kind: 'para' | 'list' | 'code'; lines: string[]; ordered?: boolean };

function parseBlocks(md: string): Block[] {
  const blocks: Block[] = [];
  let current: Block | null = null;
  let inCode = false;
  const flush = () => {
    if (current && current.lines.length > 0) blocks.push(current);
    current = null;
  };
  for (const raw of md.replace(/\r\n/g, '\n').split('\n')) {
    const line = raw.trimEnd();
    if (line.trim().startsWith('```')) {
      if (inCode) {
        flush();
        inCode = false;
      } else {
        flush();
        current = { kind: 'code', lines: [] };
        inCode = true;
      }
      continue;
    }
    if (inCode) {
      current!.lines.push(raw);
      continue;
    }
    const bullet = /^\s*[-*+]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      const ordered = !!numbered;
      if (!current || current.kind !== 'list' || current.ordered !== ordered) {
        flush();
        current = { kind: 'list', lines: [], ordered };
      }
      current.lines.push((bullet ?? numbered)![1]);
    } else if (line.trim() === '') {
      flush();
    } else {
      const text = line.replace(/^#{1,6}\s+/, '').trim();
      if (!current || current.kind !== 'para') {
        flush();
        current = { kind: 'para', lines: [] };
      }
      current.lines.push(text);
    }
  }
  flush();
  return blocks;
}

/** **bold**, *italic* / _italic_, `code` and [text](url) → text, inside one line. */
function inline(text: string): ReactNode[] {
  const out: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*\s][^*]*\*|_[^_\s][^_]*_|\[[^\]]+\]\([^)]+\))/g;
  let last = 0;
  let key = 0;
  for (const match of text.matchAll(pattern)) {
    const token = match[0];
    const at = match.index ?? 0;
    if (at > last) out.push(<Fragment key={key++}>{text.slice(last, at)}</Fragment>);
    if (token.startsWith('**')) out.push(<Text key={key++} style={styles.bold}>{token.slice(2, -2)}</Text>);
    else if (token.startsWith('`')) out.push(<Text key={key++} style={styles.inlineCode}>{token.slice(1, -1)}</Text>);
    else if (token.startsWith('[')) out.push(<Fragment key={key++}>{token.slice(1, token.indexOf(']'))}</Fragment>);
    else out.push(<Text key={key++} style={styles.italic}>{token.slice(1, -1)}</Text>);
    last = at + token.length;
  }
  if (last < text.length) out.push(<Fragment key={key++}>{text.slice(last)}</Fragment>);
  return out;
}

const mono = Platform.select({ ios: 'Menlo', android: 'monospace', default: 'ui-monospace, Menlo, monospace' });

const styles = StyleSheet.create({
  body: { ...type.body },
  small: { ...type.body, fontSize: 15, lineHeight: 15 * 1.5 },
  paragraph: { marginBottom: 12 },
  list: { marginBottom: 12 },
  item: { flexDirection: 'row', marginBottom: 4 },
  marker: { width: 22, color: colors.accent },
  itemText: { flex: 1 },
  bold: { fontFamily: fonts.serifSemiBold },
  italic: { fontFamily: fonts.serifItalic },
  inlineCode: { fontFamily: mono, fontSize: 15, backgroundColor: colors.noteFill },
  codeBox: { backgroundColor: colors.noteFill, borderRadius: 4, padding: 12, marginBottom: 12 },
  code: { fontFamily: mono, fontSize: 14, lineHeight: 20, color: colors.ink },
});
