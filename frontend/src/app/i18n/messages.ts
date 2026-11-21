import { AbilityKeyword, CardType, EndReason } from '../game/match';

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
    hp: string;
    resource: string;
    handCount: string;
    deckCount: string;
    turn: (n: number, mine: boolean) => string;
    yourHand: string;
    cost: (n: number) => string;
    neutral: string;
    backToLobby: string;
    loadFailed: string;
    play: string;
    attack: string;
    target: string;
    attackPlayer: string;
    endTurn: string;
    cancelAttack: string;
    opponentMoving: string;
    board: string;
    activeEvents: string;
    graveyard: string;
    log: string;
    attackHealth: (attack: number, health: number) => string;
    duration: (turns: number) => string;
    /** Shown instead of the duration for an event with `duration 0`. */
    instant: string;
    chooseTarget: string;
    cancelTarget: string;
    /** Event target button at the player's own HP. */
    targetPlayer: string;
    /** Event target button at the opponent's HP. */
    targetOpponent: string;
    /** The target character of an active event. */
    eventTarget: (name: string) => string;
    trapped: string;
    /** Names of keywords a character has; only the ones given by events are shown. */
    keyword: Record<AbilityKeyword, string>;
    lastPlayed: string;
    nothingPlayed: string;
    turnsLeft: (turns: number) => string;
    moveRejected: string;
    moveFailed: string;
    resultWin: string;
    resultLose: string;
    reason: Record<EndReason, string>;
    playAgain: string;
  };
  cardType: Record<CardType, string>;
}
