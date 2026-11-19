import { Messages } from './messages';

export const pl: Messages = {
  shell: {
    languageGroup: 'Język',
    pl: 'PL',
    en: 'EN',
  },
  signIn: {
    withGithub: 'Zaloguj przez GitHub',
    withGoogle: 'Zaloguj przez Google',
    loginFailed: 'Logowanie nie powiodło się — spróbuj ponownie.',
    serverUnavailable: 'Serwer gry niedostępny — spróbuj później.',
    loggedOut: 'Wylogowano.',
  },
  lobby: {
    welcome: (name) => `Witaj, ${name}`,
    newMatch: 'Nowa partia',
    logout: 'Wyloguj',
    logoutFailed: 'Nie udało się wylogować — spróbuj ponownie.',
  },
  newMatch: {
    title: 'Nowa partia',
    chooseFaction: 'Wybierz frakcję',
    start: 'Rozpocznij partię',
    backToLobby: 'Wróć do lobby',
    loadFactionsFailed: 'Nie udało się wczytać frakcji — odśwież stronę.',
    startFailed: 'Nie udało się rozpocząć partii — spróbuj ponownie.',
  },
  matchTable: {
    opponentZone: 'Przeciwnik',
    playerZone: 'Ty',
    opponentHeading: (factionName) => `Przeciwnik: ${factionName}`,
    playerHeading: (factionName) => `Ty: ${factionName}`,
    stress: 'Stres',
    resource: 'Zasób',
    handCount: 'Kart na ręce',
    deckCount: 'Kart w talii',
    turn: (n, mine) => `Tura ${n} — ${mine ? 'Twoja tura' : 'tura przeciwnika'}`,
    yourHand: 'Twoja ręka',
    cost: (n) => `Koszt: ${n}`,
    neutral: 'Neutralna',
    backToLobby: 'Wróć do lobby',
    loadFailed: 'Nie udało się wczytać partii — spróbuj ponownie.',
  },
  cardType: {
    CHARACTER: 'Postać',
    EVENT: 'Wydarzenie',
  },
};
