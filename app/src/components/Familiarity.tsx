import { type ReactNode, useEffect } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { GestureDetector, usePanGesture } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Ellipse, G, Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { colors, fonts, minTouch, type } from '@/theme';

/**
 * How familiar a learner already is with what they want to learn (issue #83): five levels, the same five the server
 * stores (`Familiarity.java`) and gives the AI, so lessons skip what's known or start from the basics.
 */
export const FAMILIARITY = [
  { level: 1, name: 'Brand new', line: 'I’ve never really looked into it.' },
  { level: 2, name: 'A little', line: 'I’ve picked up a few things here and there.' },
  { level: 3, name: 'The basics', line: 'I know the main ideas and the words.' },
  { level: 4, name: 'Comfortable', line: 'I’ve practised it. Take me deeper.' },
  { level: 5, name: 'Very confident', line: 'I know it well. Challenge me.' },
] as const;

/** Where a learner who doesn't touch the slider starts: knows a little. */
export const DEFAULT_FAMILIARITY = 2;

// A pop with a little overshoot: bouncy enough to feel playful, settling quickly (mass 1: see CLAUDE.md on springs).
const POP = { mass: 1, stiffness: 240, damping: 11 };
const SETTLE = { mass: 1, stiffness: 300, damping: 18 };

/**
 * One part of the plant, drawn in the plant's 100-unit box and grown in or out with a springy pop as the level passes
 * its threshold. With reduced motion, it simply appears or disappears.
 *
 * @param shown whether the part belongs at this level
 * @param origin where it grows from, in the box (a leaf from where it meets the stem)
 * @param delay a beat after the parts below it, so a jump of several levels grows upwards
 */
function Part({ shown, origin, delay = 0, reduced, size, children }: {
  shown: boolean; origin: [number, number]; delay?: number; reduced: boolean; size: number; children: ReactNode;
}) {
  const grown = useSharedValue(shown ? 1 : 0);
  useEffect(() => {
    grown.set(reduced ? (shown ? 1 : 0) : withDelay(shown ? delay : 0, shown ? withSpring(1, POP) : withTiming(0, { duration: 160 })));
  }, [shown, delay, reduced, grown]);
  const style = useAnimatedStyle(() => ({
    opacity: Math.min(1, grown.get() * 1.5),
    transform: [{ scale: Math.max(0, grown.get()) }],
  }));
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { transformOrigin: `${origin[0]}% ${origin[1]}%` }, style]}>
      <Svg width={size} height={size} viewBox="0 0 100 100">{children}</Svg>
    </Animated.View>
  );
}

/** A leaf leaving the stem at (x, y), pointing left or right. */
function leaf(x: number, y: number, side: -1 | 1, length: number) {
  const tip = x + side * length;
  return `M${x} ${y} C${x + side * length * 0.35} ${y - length * 0.55} ${tip - side * length * 0.15} ${y - length * 0.45} ${tip} ${y - length * 0.2}`
    + ` C${tip - side * length * 0.3} ${y + length * 0.15} ${x + side * length * 0.3} ${y + length * 0.15} ${x} ${y} Z`;
}

/** A small four-pointed sparkle centred on (x, y). */
function sparkle(x: number, y: number, r: number) {
  return `M${x} ${y - r} Q${x} ${y} ${x + r} ${y} Q${x} ${y} ${x} ${y + r} Q${x} ${y} ${x - r} ${y} Q${x} ${y} ${x} ${y - r} Z`;
}

/**
 * The slider's picture: a plant in a terracotta pot that grows with the learner's confidence. Brand new is a sleepy seed
 * that wiggles; a little, a sprout; the basics, a young plant; comfortable, a tall one with a bud; very confident, in
 * full flower with twinkling sparkles. Each part pops in with a springy bounce, the plant gives a little squash at
 * every change and sways gently. Reduced motion keeps the drawing and drops the movement. Decorative to screen
 * readers: the slider says the level in words.
 */
export function FamiliarityPlant({ level, size = 112 }: { level: number; size?: number }) {
  const reduced = useReducedMotion();
  const squash = useSharedValue(1);
  const sway = useSharedValue(0);
  const wiggle = useSharedValue(0);
  const twinkle = useSharedValue(1);

  // A squash and bounce at every change of level: the plant reacts to being told.
  useEffect(() => {
    if (reduced) return;
    squash.set(withSequence(withTiming(0.9, { duration: 90 }), withSpring(1, { mass: 1, stiffness: 320, damping: 8 })));
  }, [level, reduced, squash]);

  // Gentle movement that never stops while it's shown: a sway, the seed's wiggle, the sparkles' twinkle.
  useEffect(() => {
    if (reduced) {
      sway.set(0);
      wiggle.set(0);
      twinkle.set(1);
      return;
    }
    sway.set(withRepeat(withSequence(withTiming(2.5, { duration: 1700 }), withTiming(-2.5, { duration: 1700 })), -1, true));
    wiggle.set(withRepeat(withSequence(withTiming(-9, { duration: 260 }), withTiming(9, { duration: 260 }), withTiming(0, { duration: 260 }),
      withTiming(0, { duration: 1400 })), -1));
    twinkle.set(withRepeat(withSequence(withTiming(0.35, { duration: 700 }), withTiming(1, { duration: 700 })), -1, true));
  }, [reduced, sway, wiggle, twinkle]);

  const plantStyle = useAnimatedStyle(() => ({ transform: [{ rotateZ: `${sway.get()}deg` }, { scaleY: squash.get() }] }));
  const seedStyle = useAnimatedStyle(() => ({ transform: [{ rotateZ: `${wiggle.get()}deg` }] }));
  const sparkleStyle = useAnimatedStyle(() => ({ opacity: twinkle.get(), transform: [{ scale: 0.85 + twinkle.get() * 0.15 }] }));

  const leafFill = colors.accent;
  const stem = { stroke: colors.accent, strokeWidth: 2.6, strokeLinecap: 'round' as const, fill: 'none' };
  const at = (min: number, max = 5) => level >= min && level <= max;
  return (
    <View style={{ width: size, height: size }} accessible={false} importantForAccessibility="no-hide-descendants">
      <Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: '50% 96%' }, plantStyle]}>
        {/* Bottom to top, so each level's growth starts at the soil. */}
        <Animated.View style={[StyleSheet.absoluteFill, { transformOrigin: '50% 70%' }, seedStyle]} pointerEvents="none">
          <Part shown={at(1, 1)} origin={[50, 70]} reduced={reduced} size={size}>
            <Ellipse cx={50} cy={68.5} rx={6} ry={4} fill={colors.covers.ochre} stroke={colors.ink} strokeWidth={1.2} />
            <Path d="M50 64.5 Q52 61 55 61.5" stroke={colors.accent} strokeWidth={1.6} fill="none" strokeLinecap="round" />
          </Part>
        </Animated.View>
        <Part shown={at(2)} origin={[50, 70]} reduced={reduced} size={size}>
          <Path d="M50 70 C50 64 50 60 50 55" {...stem} />
        </Part>
        <Part shown={at(2)} origin={[50, 60]} delay={90} reduced={reduced} size={size}>
          <Path d={leaf(50, 60, -1, 11)} fill={leafFill} />
          <Path d={leaf(50, 60, 1, 11)} fill={leafFill} />
        </Part>
        <Part shown={at(3)} origin={[50, 56]} delay={150} reduced={reduced} size={size}>
          <Path d="M50 56 C50 49 51 43 50 37" {...stem} />
        </Part>
        <Part shown={at(3)} origin={[50, 46]} delay={240} reduced={reduced} size={size}>
          <Path d={leaf(50, 46, -1, 14)} fill={leafFill} />
          <Path d={leaf(50, 46, 1, 14)} fill={leafFill} />
        </Part>
        <Part shown={at(4)} origin={[50, 38]} delay={300} reduced={reduced} size={size}>
          <Path d="M50 38 C49 31 50 26 50 21" {...stem} />
        </Part>
        <Part shown={at(4)} origin={[50, 32]} delay={380} reduced={reduced} size={size}>
          <Path d={leaf(50, 32, -1, 12)} fill={leafFill} />
          <Path d={leaf(50, 32, 1, 12)} fill={leafFill} />
        </Part>
        <Part shown={at(4, 4)} origin={[50, 22]} delay={460} reduced={reduced} size={size}>
          <Ellipse cx={50} cy={17} rx={4.2} ry={6} fill={colors.covers.ochre} />
          <Path d="M46 21 Q50 16 54 21" fill={leafFill} />
        </Part>
        <Part shown={at(5)} origin={[50, 18]} delay={460} reduced={reduced} size={size}>
          <G>
            {[0, 60, 120, 180, 240, 300].map((deg) => (
              <Ellipse key={deg} cx={50} cy={8.5} rx={4.2} ry={7} fill={colors.covers.ochre} transform={`rotate(${deg} 50 16)`} />
            ))}
            <Circle cx={50} cy={16} r={4.4} fill={colors.covers.terracotta} />
          </G>
        </Part>
        {/* The pot and its soil, always there, in front of the stem's foot. */}
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <Svg width={size} height={size} viewBox="0 0 100 100">
            <Ellipse cx={50} cy={71} rx={19} ry={3.2} fill={colors.ink} opacity={0.75} />
            <Path d="M31 74 H69 L64.5 97 H35.5 Z" fill={colors.covers.terracotta} />
            <Rect x={27.5} y={69.5} width={45} height={7} rx={2} fill={colors.covers.terracotta} />
            <Rect x={27.5} y={74.5} width={45} height={1.6} fill={colors.ink} opacity={0.18} />
          </Svg>
        </View>
      </Animated.View>
      {at(5) ? (
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, sparkleStyle]}>
          <Svg width={size} height={size} viewBox="0 0 100 100">
            <Path d={sparkle(24, 22, 5)} fill={colors.covers.ochre} />
            <Path d={sparkle(78, 12, 4)} fill={colors.covers.ochre} />
            <Path d={sparkle(82, 42, 3.5)} fill={colors.covers.ochre} />
          </Svg>
        </Animated.View>
      ) : null}
    </View>
  );
}

/**
 * "How well do you know it already?" (issue #83): the plant, the level's name and a line in the learner's words, then
 * a slider of five stops between − and + buttons (as the age control in settings). Tap a stop, drag along the track,
 * or use the buttons; screen readers get one adjustable control that says the level ("Brand new: I've never really
 * looked into it"), with swipe up and down to change it.
 *
 * @param level 1 (brand new) to 5 (very confident)
 * @param onChange called with the new level
 */
export function FamiliaritySlider({ level, onChange }: { level: number; onChange: (level: number) => void }) {
  const reduced = useReducedMotion();
  const current = FAMILIARITY[level - 1];
  const width = useSharedValue(0);
  const last = useSharedValue(level);
  const thumb = useSharedValue((level - 1) / 4);

  useEffect(() => {
    last.set(level);
    thumb.set(reduced ? (level - 1) / 4 : withSpring((level - 1) / 4, SETTLE));
  }, [level, reduced, last, thumb]);

  const set = (next: number) => {
    const clamped = Math.min(5, Math.max(1, next));
    if (clamped !== level) onChange(clamped);
  };
  // Dragging along the track snaps to the nearest stop as the finger moves. Horizontal only, so the page still scrolls.
  const pan = usePanGesture({
    activeOffsetX: [-6, 6],
    failOffsetY: [-14, 14],
    onUpdate: (e) => {
      const w = width.get();
      if (w <= 0) return;
      const stop = Math.round(Math.min(1, Math.max(0, e.x / w)) * 4) + 1;
      if (stop !== last.get()) {
        last.set(stop);
        scheduleOnRN(onChange, stop);
      }
    },
  });
  const thumbStyle = useAnimatedStyle(() => ({ left: `${thumb.get() * 100}%` }));
  const fillStyle = useAnimatedStyle(() => ({ width: `${thumb.get() * 100}%` }));

  return (
    <View style={styles.block}>
      <Text style={styles.label} nativeID="familiarity-label">How well do you know it already?</Text>
      <View style={styles.picture}>
        <FamiliarityPlant level={level} />
        <View style={styles.words} accessibilityLiveRegion="polite">
          <Text style={styles.name}>{current.name}</Text>
          <Text style={styles.line}>{current.line}</Text>
        </View>
      </View>
      <View
        style={styles.stepper}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="How well do you know it already?"
        accessibilityValue={{ min: 1, max: 5, now: level, text: `${current.name}: ${current.line}` }}
        // The same, as ARIA properties: the web turns these into aria-value…, which it doesn't do for accessibilityValue.
        aria-valuemin={1}
        aria-valuemax={5}
        aria-valuenow={level}
        aria-valuetext={`${current.name}: ${current.line}`}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(e) => set(level + (e.nativeEvent.actionName === 'increment' ? 1 : -1))}>
        <Pressable accessibilityRole="button" accessibilityLabel="Less familiar" disabled={level === 1} onPress={() => set(level - 1)}
          style={({ pressed }) => [styles.step, level === 1 && styles.dim, pressed && styles.pressed]}>
          <Text style={styles.stepText}>−</Text>
        </Pressable>
        <GestureDetector gesture={pan} touchAction="pan-y">
          <View style={styles.trackArea} onLayout={(e: LayoutChangeEvent) => width.set(e.nativeEvent.layout.width)}>
            <View style={styles.track}>
              <Animated.View style={[styles.fill, fillStyle]} />
            </View>
            {FAMILIARITY.map((f) => (
              <Pressable key={f.level} accessibilityLabel={`${f.name}: ${f.line}`} accessibilityState={{ selected: f.level === level }}
                onPress={() => set(f.level)} hitSlop={6}
                style={[styles.stop, { left: `${((f.level - 1) / 4) * 100}%` }]}>
                <View style={[styles.dot, f.level <= level && styles.dotOn]} />
              </Pressable>
            ))}
            <Animated.View pointerEvents="none" style={[styles.thumb, thumbStyle]} />
          </View>
        </GestureDetector>
        <Pressable accessibilityRole="button" accessibilityLabel="More familiar" disabled={level === 5} onPress={() => set(level + 1)}
          style={({ pressed }) => [styles.step, level === 5 && styles.dim, pressed && styles.pressed]}>
          <Text style={styles.stepText}>+</Text>
        </Pressable>
      </View>
    </View>
  );
}

const THUMB = 24;

const styles = StyleSheet.create({
  block: { marginTop: 4, marginBottom: 20 },
  label: { fontFamily: fonts.sansMedium, fontSize: 14, lineHeight: 20, color: colors.ink, marginBottom: 8 },
  picture: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  words: { flex: 1, gap: 4 },
  name: { ...type.h3 },
  line: { ...type.small, fontSize: 15, lineHeight: 22, color: colors.muted },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 8 },
  step: {
    width: minTouch,
    height: minTouch,
    borderRadius: minTouch / 2,
    borderWidth: 1.5,
    borderColor: colors.accent,
    justifyContent: 'center',
    alignItems: 'center',
  },
  stepText: { fontFamily: fonts.sansSemiBold, fontSize: 20, lineHeight: 24, color: colors.accent },
  dim: { opacity: 0.4 },
  pressed: { opacity: 0.7 },
  // The track keeps a margin of half a thumb each side, so the end stops sit fully inside it.
  trackArea: { flex: 1, height: minTouch, justifyContent: 'center', marginHorizontal: THUMB / 2 },
  track: { height: 6, borderRadius: 3, backgroundColor: colors.rule, overflow: 'hidden' },
  fill: { height: 6, backgroundColor: colors.accent },
  stop: { position: 'absolute', top: 0, width: minTouch, height: minTouch, marginLeft: -minTouch / 2, justifyContent: 'center', alignItems: 'center' },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.control },
  dotOn: { backgroundColor: colors.accent },
  thumb: {
    position: 'absolute',
    top: (minTouch - THUMB) / 2,
    width: THUMB,
    height: THUMB,
    marginLeft: -THUMB / 2,
    borderRadius: THUMB / 2,
    backgroundColor: colors.paper,
    borderWidth: 2,
    borderColor: colors.accent,
    boxShadow: '0px 2px 4px rgba(41, 40, 35, 0.25)',
  },
});
