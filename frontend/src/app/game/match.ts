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

export type ResourceView = {
  current: number;
  max: number;
};

export type HandCard = {
  instanceId: number;
  cardId: string;
  name: string;
  type: CardType;
  cost: number;
  text: string;
  neutral: boolean;
};

export type PlayerView = {
  faction: FactionRef;
  stress: number;
  stressLimit: number;
  resource: ResourceView;
  deckCount: number;
  hand: HandCard[];
};

export type OpponentView = {
  faction: FactionRef;
  stress: number;
  stressLimit: number;
  resource: ResourceView;
  deckCount: number;
  handCount: number;
};

export type MatchView = {
  matchId: string;
  turn: { number: number; active: Side };
  player: PlayerView;
  opponent: OpponentView;
};
