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

export type EndReason = 'STRESS_LIMIT' | 'EMPTY_DECK';

export type AbilityKeyword = 'ON_PLAY_STRESS' | 'HEAL' | 'GUARD' | 'RUSH' | 'OVERTIME';

/** Character ability; `text` is already localized by the backend. */
export type AbilityView = {
  keyword: AbilityKeyword;
  text: string;
};

export type ResourceView = {
  current: number;
  max: number;
};

/** `attack`, `health` and `ability` are set only for characters, `duration` only for events. */
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
};

/** A character on the board; remaining HP is `health - damage`. */
export type BoardCharacter = {
  instanceId: number;
  cardId: string;
  name: string;
  attack: number;
  health: number;
  damage: number;
  canAttack: boolean;
  ability: AbilityView | null;
};

export type ActiveEvent = {
  instanceId: number;
  cardId: string;
  name: string;
  turnsLeft: number;
};

export type PlayerView = {
  faction: FactionRef;
  stress: number;
  stressLimit: number;
  resource: ResourceView;
  deckCount: number;
  hand: HandCard[];
  board: BoardCharacter[];
  activeEvents: ActiveEvent[];
  graveyardCount: number;
};

export type OpponentView = {
  faction: FactionRef;
  stress: number;
  stressLimit: number;
  resource: ResourceView;
  deckCount: number;
  handCount: number;
  board: BoardCharacter[];
  activeEvents: ActiveEvent[];
  graveyardCount: number;
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
