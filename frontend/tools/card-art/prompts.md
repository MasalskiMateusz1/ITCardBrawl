# Card art prompts

Prompts for generating card illustrations with an image model (ChatGPT / Codex `image_generation`).
Every image = the STYLE block + one card line. Save the result as `<cardId>.png`, then import with
`npm run card-art -- --from <folder>`.

## STYLE

Wide landscape illustration for a trading-card art window, aspect ratio about 16:9 (it is cropped to
1.75:1, and small cards show only the central square, so keep the main character's face and the key
gesture in the middle third).

Look: polished modern editorial cartoon / animated-feature concept art. Semi-realistic foundation:
believable anatomy, natural weight and posture, cloth folds, soft natural lighting from the upper left,
subtle texture, a sense of 3D space. Hands must be anatomically correct: five fingers, natural knuckle
and wrist bends, correct thumb placement.

Controlled exaggeration ("pick two"): push exactly two features of each character to comic extremes
(e.g. huge chin + tiny squinting eyes, giant ears + sweeping mustache, towering lanky frame + tiny head)
and keep everything else grounded. Strong curved line of action: mid-motion, off-balance, full of
personality, never standing stiffly. Clear, readable hand acting (pointing, head-scratching, pinching
the bridge of the nose, ranting gestures). Quirky imperfect everyday office outfits: ill-fitting suits,
oversized hoodies and sweaters, loose ties, lanyards, mismatched socks. Varied ages, body types and skin
tones. No superhero builds, no anime faces.

Setting: dark, moody background (near-black navy) so the art reads on a dark game board, with the
card's faction colour as the dominant accent: {FACTION}.

Hard rules: no text, letters, numbers, captions, speech-bubble words or logos anywhere in the image; no
real people or celebrity likenesses; no existing cartoon, game, film or meme characters (only the joke
is borrowed, the characters are original); no watermark, no border, no frame.

## Factions

- developers: cool blue and cyan accents
- testers: warm orange and amber accents
- neutral: muted slate grey with small accents of yellow, red and green

## Cards

| cardId | faction | scene |
|---|---|---|
| dev-junior | developers | An eager junior developer in an oversized hoodie slams the deploy button while flinging a test checklist over their shoulder; a rocket launches behind them (the "push straight to main, tests optional" joke). |
| dev-stackoverflow | developers | A developer's hand hovers over a tiny keyboard with only three giant keys: copy, paste, and an "ask the forum" key shown as an icon (no letters). |
| dev-fullstack | developers | A developer proudly presents a horse drawing on an easel: the front half is a polished masterpiece, the back half a crude stick sketch. |
| dev-works-on-my-machine | developers | A smug developer watches a crane lift their whole desk and PC, packed in a shipping crate, to be shipped to production ("then we'll ship your machine"). |
| dev-friday-hotfix | developers | Late Friday afternoon: a cackling developer presses deploy while, behind them, lightning sets a weekend beach chair and parasol on fire; a wall clock shows nearly five. |
| dev-backend | developers | A backend developer clutches their cheeks in horror as a box they tried to center escapes out of the browser window's corner. |
| dev-force-push | developers | A developer thrusts one palm forward like a space wizard (original, no robes or film references), blasting teammates' little commit nodes off a branch. |
| dev-senior | developers | A seasoned senior developer with a walrus mustache gives a huge, theatrical "it depends" shrug under a signpost whose both arrows carry green checkmarks. |
| dev-legacy-refactor | developers | A nervous developer pinches one block out of a towering, swaying Jenga-like tower of legacy code blocks; red warning lights flare around it. |
| dev-tech-lead | developers | A tech lead squints through a magnifying glass at a tiny code diff covered in red review marks, while a gigantic scroll of code next to them carries a single green check. |
| tst-intern | testers | A hyperactive intern tester clicks everything at once on a cracking screen, arms a blur of motion, a swarm of mouse cursors everywhere (monkey testing). |
| tst-vague-bug-report | testers | A phone photo of a monitor: blurry moiré screen, a fingertip covering the lens, an angry generic sad-face bug icon; the reporter's hand grips the phone edge. |
| tst-manual | testers | A manual tester pushes a huge boulder shaped like a mouse cursor up a steep hill (Sisyphus), tally marks scratched on the slope, a tiny bug waiting at the top. |
| tst-edge-case | testers | A bar counter: a tester has ordered one beer, an upside-down glass, a pyramid of glasses, and a confused lizard sitting on the bar (the "QA engineer walks into a bar" joke). |
| tst-regression | testers | A tester's hammer squashes one bug, and two new bugs crawl out from under it; a boomerang flies back in the background. |
| tst-automation | testers | A delighted test robot gives two thumbs up beside two drawers that each pass alone but block each other when opened together. |
| tst-flaky-test | testers | A tester yanks the lever of a slot machine whose reels show green checks and red crosses, hoping for all green. |
| tst-performance | testers | A tester unleashes a swarm of cartoon wasps carrying tiny rockets at a terrified, sweating server rack (load test = DDoS yourself). |
| tst-production-bug | testers | 3 AM: an on-call tester jolts upright in bed, bed-head hair, eyes bulging, as a phone on the nightstand screams with a red bug alert; moon in the window. |
| tst-qa-lead | testers | A bearded QA lead plants their feet on a narrow bridge, one giant palm raised to stop a queue of release crates: "you shall not pass" (original character, no wizard costume). |
| neu-coffee | neutral | A blissful programmer with a coffee funnel on their head; coffee pours in and a ribbon of code streams out of their typing hands. |
| neu-daily-standup | neutral | A "15-minute" stand-up that has lasted an hour: four exhausted colleagues covered in cobwebs, the wall clock spinning. |
| neu-rubber-duck | neutral | A developer explains a tangled problem to a rubber duck on the desk; the duck has a sudden lightbulb moment. |
| neu-scrum-master | neutral | A scrum master proudly points at a calendar wall packed with nested meeting invites (a meeting to plan the meeting), holding a broom. |
| neu-sprint-planning | neutral | A planning-poker table: every team member's hand shows the same card with three pips (no digits). |
| neu-devops | neutral | A DevOps engineer inspects a red pipeline with a magnifying glass and finds the fault at a small globe icon (the "it was DNS" joke). |
| neu-project-manager | neutral | A smug project manager raises one finger ("just one small change") while standing on a tiny iceberg tip; below the waterline a huge iceberg holds databases, gears and bugs. |
| neu-meeting | neutral | A bored meeting table: three colleagues dozing and eye-rolling, a shared thought bubble showing an envelope ("this meeting could have been an email"). |
| neu-deadline | neutral | A panicking person bends backwards as a winged, angry calendar page flies straight at them, whooshing motion lines. |
| neu-requirements-change | neutral | A bridge under construction that, halfway across, turns into a tunnel entrance; a dusty old email envelope lies on the blueprint. |
| neu-client | neutral | A client in an ill-fitting suit daydreams about a tyre swing on a tree, while the real build is a plank tied awkwardly to the trunk (the tree-swing cartoon joke, original drawing). |
