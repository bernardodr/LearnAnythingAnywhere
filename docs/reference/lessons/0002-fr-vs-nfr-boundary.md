# Blurs functional vs non-functional; misses the read path

In the first Bitly requirements attempt (lesson 0001), the learner listed "persist URLs with expiry" as a non-functional requirement. They phrased scale as a goal ("scale up/down to minimise cost") instead of numbers. They omitted the redirect, which is the core read path, and didn't mention CAP / availability. They did quantify latency well (<100 ms).

**Implications:** Future lessons should drill (a) "does vs. is" (behaviour vs. quality), (b) always asking "what's the read path?", since read-heavy systems are common, and (c) stating availability vs. consistency explicitly. They also tend to add costly features (editing links), so reinforce scoping and saying what's out of scope.
