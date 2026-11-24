import type { ReactNode } from 'react';
import Svg, { Circle, Ellipse, G, Line, Path, Rect } from 'react-native-svg';

/**
 * The subject motifs on book covers (`docs/DESIGN.md`, "Books"): a fixed, hand-drawn set of restrained line drawings,
 * one per kind of subject. The server keeps the names in step (`com.laa.textbook.Motif`), and the AI picks one when a
 * preface ends. `ornament` is the neutral one, for a book that has none yet.
 *
 * Each is drawn in a 100 × 100 box in one colour (the cover's title colour), mostly as lines, so they sit quietly on
 * the cloth. Add a motif here, in `Motif.java`, and in the mission prompt's list, all three together.
 */
export const MOTIFS = [
  'stars', 'moon', 'leaf', 'wheat', 'mountain', 'wave', 'gear', 'dots', 'shapes', 'brush', 'note',
  'flask', 'atom', 'globe', 'quill', 'column', 'bars', 'pulse', 'pawn', 'speech', 'ornament',
] as const;

export type MotifName = (typeof MOTIFS)[number];

/** A known motif's name, or the neutral ornament for anything else (an older book, or a name this app doesn't know). */
export function motifOrDefault(name: string | null | undefined): MotifName {
  return (MOTIFS as readonly string[]).includes(name ?? '') ? (name as MotifName) : 'ornament';
}

/** A four-pointed star with curved sides, centred on (x, y), reaching `s` each way. */
function star(x: number, y: number, s: number) {
  return `M${x} ${y - s} Q${x} ${y} ${x + s} ${y} Q${x} ${y} ${x} ${y + s} Q${x} ${y} ${x - s} ${y} Q${x} ${y} ${x} ${y - s} Z`;
}

/** An almond leaf from (0, 0) along the x axis, `len` long, with its midrib. */
function leaf(x: number, y: number, angle: number, len: number, key: string, c: string, w: number) {
  const d = `M0 0 C${len * 0.3} ${-len * 0.32} ${len * 0.7} ${-len * 0.32} ${len} 0 C${len * 0.7} ${len * 0.32} ${len * 0.3} ${len * 0.32} 0 0 Z`;
  return (
    <G key={key} transform={`translate(${x} ${y}) rotate(${angle})`}>
      <Path d={d} stroke={c} strokeWidth={w} fill="none" strokeLinejoin="round" />
      <Path d={`M0 0 L${len * 0.85} 0`} stroke={c} strokeWidth={w * 0.6} />
    </G>
  );
}

/** The drawings. `c` is the colour, `w` the line width; filled marks use `c` too. */
function drawing(name: MotifName, c: string, w: number): ReactNode {
  const line = { stroke: c, strokeWidth: w, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  switch (name) {
    case 'stars':
      return (
        <>
          <Circle cx={50} cy={50} r={36} {...line} />
          <Path d={star(60, 40, 11)} fill={c} />
          <Path d={star(39, 62, 7)} fill={c} />
          {[[34, 34, 1.4], [67, 64, 1.4], [53, 74, 1.1], [28, 50, 1.1], [70, 28, 1.1], [45, 24, 1], [75, 48, 1.2], [40, 44, 0.9]].map(
            ([x, y, r]) => <Circle key={`${x}-${y}`} cx={x} cy={y} r={r} fill={c} />,
          )}
        </>
      );
    case 'moon':
      return (
        <>
          <Path d="M62 20 A31 31 0 1 0 62 80 A25 25 0 1 1 62 20 Z" {...line} />
          <Path d={star(76, 34, 5)} fill={c} />
          <Path d={star(80, 60, 3.5)} fill={c} />
        </>
      );
    case 'leaf':
      return (
        <>
          <Path d="M28 88 C42 68 54 46 72 14" {...line} />
          {leaf(36, 76, -118, 18, 'a', c, w)}
          {leaf(41, 68, -18, 17, 'b', c, w)}
          {leaf(48, 56, -125, 18, 'c', c, w)}
          {leaf(54, 46, -25, 16, 'd', c, w)}
          {leaf(61, 34, -128, 15, 'e', c, w)}
          {leaf(66, 25, -35, 13, 'f', c, w)}
        </>
      );
    case 'wheat':
      return (
        <>
          <Path d="M50 90 L50 22" {...line} />
          {[30, 42, 54, 66].map((y) => (
            <G key={y}>
              <Ellipse cx={43} cy={y} rx={4} ry={8} transform={`rotate(-32 43 ${y})`} {...line} />
              <Ellipse cx={57} cy={y} rx={4} ry={8} transform={`rotate(32 57 ${y})`} {...line} />
            </G>
          ))}
          <Ellipse cx={50} cy={17} rx={3.5} ry={7.5} {...line} />
        </>
      );
    case 'mountain':
      return (
        <>
          <Path d="M10 78 L38 36 L50 54 L62 40 L90 78 Z" {...line} />
          <Path d="M30 48 L35 44 L39 48 L43 44 L46 46" {...line} />
          <Circle cx={72} cy={24} r={7} {...line} />
          <Line x1={6} y1={86} x2={94} y2={86} {...line} />
        </>
      );
    case 'wave':
      return (
        <>
          {[36, 50, 64].map((y) => (
            <Path key={y} d={`M12 ${y} C20 ${y - 9} 30 ${y - 9} 38 ${y} S56 ${y + 9} 64 ${y} S80 ${y - 9} 88 ${y}`} {...line} />
          ))}
        </>
      );
    case 'gear':
      return (
        <>
          {Array.from({ length: 8 }, (_, i) => {
            const a = (i * Math.PI) / 4;
            return (
              <Line key={i} x1={50 + 21 * Math.cos(a)} y1={50 + 21 * Math.sin(a)} x2={50 + 29 * Math.cos(a)} y2={50 + 29 * Math.sin(a)}
                stroke={c} strokeWidth={w * 4} strokeLinecap="butt" />
            );
          })}
          <Circle cx={50} cy={50} r={21} {...line} />
          <Circle cx={50} cy={50} r={8} {...line} />
        </>
      );
    case 'dots':
      return (
        <>
          {[22, 34, 46, 58, 70, 82].flatMap((y) =>
            [26, 38, 50, 62, 74].map((x) => <Circle key={`${x}-${y}`} cx={x} cy={y} r={2.1} fill={c} />),
          )}
        </>
      );
    case 'shapes':
      return (
        <>
          <Path d="M20 28 H80 A30 30 0 0 1 20 28 Z" fill={c} opacity={0.92} />
          <Path d="M20 90 L20 62 C52 62 80 74 80 90 Z" fill={c} opacity={0.5} />
        </>
      );
    case 'brush':
      return (
        <>
          <Line x1={74} y1={14} x2={47} y2={55} stroke={c} strokeWidth={w * 2.2} strokeLinecap="round" />
          <Line x1={47} y1={55} x2={41} y2={64} stroke={c} strokeWidth={w * 3.4} strokeLinecap="butt" />
          <Path d="M41 64 C33 70 30 80 22 86 C35 85 44 78 45 68 Z" fill={c} />
          <Path d="M14 92 C34 86 56 94 86 88" {...line} />
        </>
      );
    case 'note':
      return (
        <>
          <Ellipse cx={36} cy={72} rx={7.5} ry={5.5} transform="rotate(-20 36 72)" fill={c} />
          <Ellipse cx={67} cy={63} rx={7.5} ry={5.5} transform="rotate(-20 67 63)" fill={c} />
          <Line x1={43} y1={70} x2={43} y2={26} {...line} />
          <Line x1={74} y1={61} x2={74} y2={17} {...line} />
          <Line x1={43} y1={26} x2={74} y2={17} stroke={c} strokeWidth={w * 3} strokeLinecap="butt" />
        </>
      );
    case 'flask':
      return (
        <>
          <Path d="M40 16 H60 M44 16 V42 L26 80 Q24 86 30 86 H70 Q76 86 74 80 L56 42 V16" {...line} />
          <Path d="M34 66 H66" {...line} />
          <Circle cx={46} cy={56} r={2.2} {...line} />
          <Circle cx={54} cy={49} r={1.6} {...line} />
        </>
      );
    case 'atom':
      return (
        <>
          {[0, 60, 120].map((a) => (
            <Ellipse key={a} cx={50} cy={50} rx={36} ry={12} transform={`rotate(${a} 50 50)`} {...line} />
          ))}
          <Circle cx={50} cy={50} r={4.5} fill={c} />
        </>
      );
    case 'globe':
      return (
        <>
          <Circle cx={50} cy={50} r={33} {...line} />
          <Ellipse cx={50} cy={50} rx={14} ry={33} {...line} />
          <Line x1={17} y1={50} x2={83} y2={50} {...line} />
          <Path d="M22 34 H78 M22 66 H78" {...line} />
        </>
      );
    case 'quill':
      return (
        <>
          <Path d="M76 12 C52 20 36 44 31 76 C45 60 63 42 76 12 Z" {...line} />
          <Line x1={76} y1={12} x2={26} y2={88} {...line} />
          <Line x1={14} y1={90} x2={44} y2={90} {...line} />
        </>
      );
    case 'column':
      return (
        <>
          <Path d="M26 20 H74 M31 27 H69" {...line} />
          <Path d="M34 27 V78 M66 27 V78 M42 30 V75 M50 30 V75 M58 30 V75" {...line} />
          <Path d="M30 78 H70 M24 86 H76" {...line} />
        </>
      );
    case 'bars':
      return (
        <>
          {[[22, 18], [37, 30], [52, 42], [67, 56]].map(([x, h]) => (
            <Rect key={x} x={x} y={80 - h} width={10} height={h} {...line} />
          ))}
          <Line x1={14} y1={80} x2={86} y2={80} {...line} />
          <Path d="M22 46 L40 38 L56 28 L80 14 M70 14 H80 V24" {...line} />
        </>
      );
    case 'pulse':
      return (
        <>
          <Path d="M10 54 H32 L39 40 L48 72 L57 28 L64 54 H90" {...line} />
          <Circle cx={50} cy={50} r={36} {...line} opacity={0.45} />
        </>
      );
    case 'pawn':
      return (
        <>
          <Circle cx={50} cy={28} r={10} {...line} />
          <Path d="M42 40 H58 M45 40 C45 54 40 64 34 72 H66 C60 64 55 54 55 40" {...line} />
          <Rect x={28} y={72} width={44} height={9} rx={2} {...line} />
        </>
      );
    case 'speech':
      return (
        <>
          <Path d="M24 18 H60 Q66 18 66 24 V46 Q66 52 60 52 H38 L27 62 V52 H24 Q18 52 18 46 V24 Q18 18 24 18 Z" {...line} />
          <Path d="M46 58 H78 Q84 58 84 64 V80 Q84 86 78 86 H76 V94 L67 86 H46 Q40 86 40 80 V64 Q40 58 46 58 Z" {...line} />
          <Path d="M28 32 H56 M28 40 H48" {...line} />
        </>
      );
    case 'ornament':
      return (
        <>
          <Circle cx={50} cy={50} r={24} {...line} />
          <Path d={star(50, 50, 15)} {...line} />
          {[[50, 16], [84, 50], [50, 84], [16, 50]].map(([x, y]) => <Circle key={`${x}-${y}`} cx={x} cy={y} r={2} fill={c} />)}
        </>
      );
  }
}

/**
 * One motif, `size` wide and tall. Decorative: screen readers skip it (a cover's label already names the book).
 *
 * @param name the motif (`motifOrDefault` turns anything unknown into the neutral ornament)
 * @param size its width and height in points
 * @param color its one colour: the cover's title colour, so it always contrasts with the cloth
 * @param strokeWidth line width in the 100-unit box; thinner reads more restrained on small covers
 */
export function Motif({ name, size, color, strokeWidth = 1.8 }: { name: MotifName; size: number; color: string; strokeWidth?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100" accessible={false} importantForAccessibility="no-hide-descendants"
      aria-hidden>
      {drawing(name, color, strokeWidth)}
    </Svg>
  );
}
