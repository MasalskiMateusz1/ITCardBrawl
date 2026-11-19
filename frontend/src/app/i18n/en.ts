import { Messages } from './messages';

export const en: Messages = {
  shell: {
    languageGroup: 'Language',
    pl: 'PL',
    en: 'EN',
  },
  signIn: {
    withGithub: 'Sign in with GitHub',
    withGoogle: 'Sign in with Google',
    loginFailed: 'Sign-in failed — please try again.',
    serverUnavailable: 'Game server unavailable — please try later.',
    loggedOut: 'Signed out.',
  },
  lobby: {
    welcome: (name) => `Welcome, ${name}`,
    newMatch: 'New match',
    logout: 'Sign out',
    logoutFailed: 'Could not sign out — please try again.',
  },
  newMatch: {
    title: 'New match',
    chooseFaction: 'Choose a faction',
    start: 'Start match',
    backToLobby: 'Back to lobby',
    loadFactionsFailed: 'Could not load factions — refresh the page.',
    startFailed: 'Could not start the match — please try again.',
  },
  matchTable: {
    opponentZone: 'Opponent',
    playerZone: 'You',
    opponentHeading: (factionName) => `Opponent: ${factionName}`,
    playerHeading: (factionName) => `You: ${factionName}`,
    stress: 'Stress',
    resource: 'Resource',
    handCount: 'Cards in hand',
    deckCount: 'Cards in deck',
    turn: (n, mine) => `Turn ${n} — ${mine ? 'your turn' : "opponent's turn"}`,
    yourHand: 'Your hand',
    cost: (n) => `Cost: ${n}`,
    neutral: 'Neutral',
    backToLobby: 'Back to lobby',
    loadFailed: 'Could not load the match — please try again.',
  },
  cardType: {
    CHARACTER: 'Character',
    EVENT: 'Event',
  },
};
