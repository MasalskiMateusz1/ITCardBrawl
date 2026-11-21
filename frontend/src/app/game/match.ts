/** Mirror of the backend JSON contract (MatchController.FactionSummary, MatchView). */

export type FactionSummary = {
  id: string;
  name: string;
  description: string;
};

export type FactionRef = {
  id: string;
  name: string;
};

export type Side = 'PLAYER' | 'OPPONENT';

export type CardType = 'CHARACTER' | 'EVENT';

export type MatchStatus = 'IN_PROGRESS' | 'FINISHED';

export type EndReason = 'HP_DEPLETED' | 'EMPTY_DECK';

export type AbilityKeyword = 'ON_PLAY_DAMAGE' | 'HEAL' | 'GUARD' | 'RUSH' | 'OVERTIME';

/** Character ability; `text` is already localized by the backend. */
export type AbilityView = {
  keyword: AbilityKeyword;
  text: string;
};

export type EffectKind =
  | 'PLAYER_DAMAGE'
  | 'DAMAGE'
  | 'HEAL'
  | 'DRAW'
  | 'BUDGET_GAIN'
  | 'BUDGET_MAX'
  | 'ATTACK'
  | 'HEALTH'
  | 'GUARD'
  | 'RUSH'
  | 'TRAP';

export type EffectScope =
  | 'TARGET'
  | 'OPPONENT'
  | 'SELF'
  | 'OWN_BOARD'
  | 'ENEMY_BOARD'
  | 'OWN_ALL'
  | 'ENEMY_ALL'
  | 'ALL_BOARDS';

/** An event effect; `text` is already localized by the backend. */
export type EffectView = {
  kind: EffectKind;
  scope: EffectScope;
  amount: number;
  text: string;
};

/**
 * A target of an event or an attack (MatchView.TargetView / MatchController.TargetRequest):
 * a character by `instanceId` or a player by `side`. The backend sends the unused field as `null`;
 * the SPA omits it in requests.
 */
export type Target =
  | { kind: 'CHARACTER'; instanceId: number; side?: null }
  | { kind: 'PLAYER'; side: Side; instanceId?: null };

/** The last event a side played; `effects` are already localized. */
export type PlayedEvent = {
  instanceId: number;
  name: string;
  effects: EffectView[];
};

export type BudgetView = {
  current: number;
  max: number;
};

/**
 * `attack`, `health` and `ability` are set only for characters, `duration` and `effects` only for
 * events (`duration 0` = instant). `playable` and `targets` come from the backend's legal moves.
 */
export type HandCard = {
  instanceId: number;
  cardId: string;
  name: string;
  type: CardType;
  cost: number;
  text: string;
  neutral: boolean;
  attack: number | null;
  health: number | null;
  ability: AbilityView | null;
  duration: number | null;
  effects: EffectView[];
  requiresTarget: boolean;
  playable: boolean;
  targets: Target[];
};

/**
 * A character on the board. `attack` and `health` are effective (active events applied), remaining HP
 * is `health - damage`. `keywords` are effective; `attackTargets` is empty for opponent characters.
 */
export type BoardCharacter = {
  instanceId: number;
  cardId: string;
  name: string;
  attack: number;
  health: number;
  damage: number;
  canAttack: boolean;
  ability: AbilityView | null;
  baseAttack: number;
  baseHealth: number;
  keywords: AbilityKeyword[];
  trapped: boolean;
  attackTargets: Target[];
};

export type ActiveEvent = {
  instanceId: number;
  cardId: string;
  name: string;
  turnsLeft: number;
  effects: EffectView[];
  /** The target character's name, or `null` when the event has no character target. */
  targetName: string | null;
};

export type PlayerView = {
  faction: FactionRef;
  hp: number;
  maxHp: number;
  budget: BudgetView;
  deckCount: number;
  hand: HandCard[];
  board: BoardCharacter[];
  activeEvents: ActiveEvent[];
  graveyardCount: number;
  lastEvent: PlayedEvent | null;
};

export type OpponentView = {
  faction: FactionRef;
  hp: number;
  maxHp: number;
  budget: BudgetView;
  deckCount: number;
  handCount: number;
  board: BoardCharacter[];
  activeEvents: ActiveEvent[];
  graveyardCount: number;
  lastEvent: PlayedEvent | null;
};

export type MatchResult = {
  winner: Side;
  reason: EndReason;
};

/** A log line, already localized by the backend. */
export type LogEntry = {
  side: Side;
  text: string;
};

export type TurnView = { number: number; active: Side };

/**
 * The table after one computer move. The log is sent once, in the final state:
 * the step's log is `final.log.slice(0, logSize)`.
 */
export type ReplayStep = {
  turn: TurnView;
  status: MatchStatus;
  result: MatchResult | null;
  player: PlayerView;
  opponent: OpponentView;
  logSize: number;
};

/** `replay` lists the computer's moves to play back before the final state; usually empty. */
export type MatchView = {
  matchId: string;
  status: MatchStatus;
  turn: TurnView;
  player: PlayerView;
  opponent: OpponentView;
  result: MatchResult | null;
  log: LogEntry[];
  replay: ReplayStep[];
};
