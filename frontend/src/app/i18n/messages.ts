import { CardType } from '../game/match';

/** Every user-visible UI text, grouped per screen. Each language must provide all of them. */
export interface Messages {
  shell: {
    languageGroup: string;
    pl: string;
    en: string;
  };
  signIn: {
    withGithub: string;
    withGoogle: string;
    loginFailed: string;
    serverUnavailable: string;
    loggedOut: string;
  };
  lobby: {
    welcome: (name: string) => string;
    newMatch: string;
    logout: string;
    logoutFailed: string;
  };
  newMatch: {
    title: string;
    chooseFaction: string;
    start: string;
    backToLobby: string;
    loadFactionsFailed: string;
    startFailed: string;
  };
  matchTable: {
    opponentZone: string;
    playerZone: string;
    opponentHeading: (factionName: string) => string;
    playerHeading: (factionName: string) => string;
    stress: string;
    resource: string;
    handCount: string;
    deckCount: string;
    turn: (n: number, mine: boolean) => string;
    yourHand: string;
    cost: (n: number) => string;
    neutral: string;
    backToLobby: string;
    loadFailed: string;
  };
  cardType: Record<CardType, string>;
}
