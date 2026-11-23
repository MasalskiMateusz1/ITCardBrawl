import type { BoardCharacter, MatchView, ReplayStep } from '../../src/app/game/match';
import { midGame } from './mid-game';

/**
 * The mid-game match won by the player: the opponent's HP ran out on the player's turn 5, so the
 * game-over overlay shows over the final board.
 */
export const gameOverWin: MatchView = {
  ...midGame,
  status: 'FINISHED',
  turn: { number: 5, active: 'PLAYER', timeLimitSeconds: null },
  opponent: { ...midGame.opponent, hp: 0 },
  result: { winner: 'PLAYER', reason: 'HP_DEPLETED' },
  log: [
    ...midGame.log,
    { side: 'PLAYER', text: 'Full-stack Developer atakuje przeciwnika', reason: false },
    { side: 'PLAYER', text: 'HP przeciwnika: 4 → 0/30', reason: false },
  ],
};

/** The opponent's QA intern, played during the replayed turn. */
const intern: BoardCharacter = {
  instanceId: 203,
  cardId: 'tst-intern',
  name: 'Stażysta QA',
  attack: 1,
  health: 1,
  damage: 0,
  canAttack: false,
  ability: {
    keyword: 'ON_PLAY_DAMAGE',
    text: 'Po zagraniu: zadaj przeciwnikowi 1 obrażenie',
  },
  baseAttack: 1,
  baseHealth: 1,
  keywords: ['ON_PLAY_DAMAGE'],
  trapped: false,
  attackTargets: [],
  cost: 1,
  faction: 'testers',
};

const opponentTurn = { number: 5, active: 'OPPONENT' as const, timeLimitSeconds: null };

/** The log of the final state; each replay step shows its first `logSize` entries. */
const replayLog: MatchView['log'] = [
  ...midGame.log,
  { side: 'PLAYER', text: 'Kończysz turę', reason: false },
  { side: 'OPPONENT', text: 'Tura przeciwnika 5', reason: false },
  { side: 'OPPONENT', text: 'Przeciwnik dokłada tanią postać', reason: true },
  { side: 'OPPONENT', text: 'Przeciwnik zagrywa: Stażysta QA', reason: false },
  { side: 'OPPONENT', text: 'Stażysta QA zadaje 1 obrażenie: HP 24 → 23/30', reason: false },
  { side: 'PLAYER', text: 'Twoja tura 6', reason: false },
];

/** Step 1: the opponent's turn begins, nothing played yet. */
const turnStart: ReplayStep = {
  turn: opponentTurn,
  status: 'IN_PROGRESS',
  result: null,
  player: midGame.player,
  opponent: { ...midGame.opponent, budget: { current: 5, max: 5 } },
  logSize: midGame.log.length + 2,
  reason: null,
};

/** Step 2: the opponent played its intern, which hit the player for 1. */
const internPlayed: ReplayStep = {
  ...turnStart,
  player: { ...midGame.player, hp: 23 },
  opponent: {
    ...midGame.opponent,
    budget: { current: 4, max: 5 },
    handCount: 3,
    board: [...midGame.opponent.board, intern],
  },
  logSize: midGame.log.length + 5,
  reason: 'Przeciwnik dokłada tanią postać',
};

/**
 * The player ended turn 5: the response plays back the opponent's turn (two steps shown 700 ms
 * apart) before the final state, the player's turn 6. Shown at step 2, mid-playback.
 */
export const opponentReplay: MatchView = {
  ...midGame,
  turn: { number: 6, active: 'PLAYER', timeLimitSeconds: 45 },
  player: { ...midGame.player, hp: 23 },
  opponent: internPlayed.opponent,
  log: replayLog,
  replay: [turnStart, internPlayed, { ...internPlayed, logSize: replayLog.length }],
};

/**
 * The mid-game board after the opponent's "Niestabilny test" trapped the player's Backendowiec:
 * the opponent has two active events (a fanned stack), the newer one with a target.
 */
export const eventStack: MatchView = {
  ...midGame,
  player: {
    ...midGame.player,
    board: midGame.player.board.map((c) => (c.instanceId === 113 ? { ...c, trapped: true } : c)),
  },
  opponent: {
    ...midGame.opponent,
    activeEvents: [
      ...midGame.opponent.activeEvents,
      {
        instanceId: 222,
        cardId: 'tst-flaky-test',
        name: 'Niestabilny test',
        turnsLeft: 3,
        effects: [{ kind: 'TRAP', scope: 'TARGET', amount: 0, text: 'Uwięź wybraną postać' }],
        targetName: 'Backendowiec',
        cost: 4,
        faction: 'testers',
      },
    ],
  },
};
