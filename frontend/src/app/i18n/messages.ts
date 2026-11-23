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
    /** Accessible names of the aside status panels. */
    opponentStatus: string;
    playerStatus: string;
    hp: string;
    budget: string;
    handCount: string;
    deckCount: string;
    turn: (n: number, mine: boolean) => string;
    yourHand: string;
    /** The exit link at the bottom of the aside (to the lobby). */
    exit: string;
    loadFailed: string;
    /** Accessible names of the card buttons: the action plus the card (or player) name. */
    playCard: (name: string) => string;
    selectAttacker: (name: string) => string;
    attackCard: (name: string) => string;
    targetCard: (name: string) => string;
    /** A board character's name with its Guard keyword, for its button's accessible name. */
    guardedName: (name: string, guard: string) => string;
    /** An empty board slot (read by screen readers only). */
    emptySlot: string;
    endTurn: string;
    /** Remaining time of the player's turn, shown next to the end-turn button. */
    timeLeft: (seconds: number) => string;
    cancelAttack: string;
    opponentMoving: string;
    board: string;
    activeEvents: string;
    /** Accessible name of a zone's active events stack (opens the active events overlay). */
    showActiveEvents: (count: number) => string;
    /** Titles of the active events overlays. */
    opponentActiveEvents: string;
    playerActiveEvents: string;
    graveyard: string;
    /** Accessible name of a status panel's graveyard button (opens the graveyard overlay). */
    showGraveyard: (count: number) => string;
    /** A graveyard card's button: shows the whole card with its ability. */
    showCardDetails: (name: string) => string;
    /** Titles of the graveyard overlays. */
    opponentGraveyard: string;
    playerGraveyard: string;
    /** Close button of an overlay. */
    close: string;
    /** Empty list placeholder (no active events, empty graveyard). */
    none: string;
    /** Status shown while the match is loading. */
    loading: string;
    log: string;
    chooseTarget: string;
    cancelTarget: string;
    /** The target character of an active event. */
    eventTarget: (name: string) => string;
    /** Names of the keywords a character has. */
    keyword: Record<AbilityKeyword, string>;
    moveRejected: string;
    moveFailed: string;
    resultWin: string;
    resultLose: string;
    reason: Record<EndReason, string>;
    playAgain: string;
  };
  cardType: Record<CardType, string>;
  /** Labels of the shared card (ui/card). */
  card: {
    /** Accessible name of the cost badge. */
    cost: (n: number) => string;
    /** Accessible name of the attack badge. */
    attack: (n: number) => string;
    /** Accessible name of the HP badge (remaining HP). */
    hp: (n: number) => string;
    /** Placeholder text in the art window when there is no image. */
    art: string;
    trapped: string;
    /** Event duration; `instant` is used for `duration 0`. */
    duration: (turns: number) => string;
    instant: string;
    /** Turns left of an active event. */
    turnsLeft: (turns: number) => string;
    /** Faction names on the type line, by faction id; unknown ids show the type only. */
    faction: Record<string, string>;
  };
}
