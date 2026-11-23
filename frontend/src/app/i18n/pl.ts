import { Messages } from './messages';

/** Polish plural of "tura": 1 tura, 2–4 tury (not 12–14), otherwise tur. */
function turns(n: number): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (n === 1) {
    return `${n} tura`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${n} tury`;
  }
  return `${n} tur`;
}

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
    opponentStatus: 'Status przeciwnika',
    playerStatus: 'Twój status',
    hp: 'HP',
    budget: 'Budżet',
    handCount: 'Kart na ręce',
    deckCount: 'Kart w talii',
    turn: (n, mine) => `Tura ${n} — ${mine ? 'Twoja tura' : 'tura przeciwnika'}`,
    yourHand: 'Twoja ręka',
    exit: 'Wyjdź do menu',
    loadFailed: 'Nie udało się wczytać partii — spróbuj ponownie.',
    playCard: (name) => `Zagraj: ${name}`,
    selectAttacker: (name) => `Wybierz do ataku: ${name}`,
    attackCard: (name) => `Atakuj: ${name}`,
    targetCard: (name) => `Cel: ${name}`,
    guardedName: (name, guard) => `${name} (${guard})`,
    emptySlot: 'Puste miejsce',
    endTurn: 'Zakończ turę',
    timeLeft: (s) => `Pozostało: ${s} s`,
    cancelAttack: 'Anuluj atak',
    opponentMoving: 'Ruch przeciwnika…',
    board: 'Stół',
    activeEvents: 'Aktywne wydarzenia',
    graveyard: 'Cmentarz',
    showGraveyard: (count) => `Cmentarz ${count} — pokaż karty`,
    showCardDetails: (name) => `Pokaż kartę: ${name}`,
    opponentGraveyard: 'Cmentarz przeciwnika',
    playerGraveyard: 'Twój cmentarz',
    close: 'Zamknij',
    none: 'brak',
    loading: 'Ładowanie partii…',
    log: 'Przebieg partii',
    chooseTarget: 'Wybierz cel wydarzenia',
    cancelTarget: 'Anuluj',
    eventTarget: (name) => `Cel: ${name}`,
    keyword: {
      ON_PLAY_DAMAGE: 'Obrażenia po zagraniu',
      HEAL: 'Leczenie po zagraniu',
      GUARD: 'Ochrona',
      RUSH: 'Szarża',
      OVERTIME: 'Nadgodziny',
    },
    turnsLeft: (turns) => `Pozostało tur: ${turns}`,
    moveRejected: 'Ten ruch jest niedozwolony.',
    moveFailed: 'Nie udało się wykonać ruchu — spróbuj ponownie.',
    resultWin: 'Wygrana',
    resultLose: 'Przegrana',
    reason: {
      HP_DEPLETED: 'Przyczyna: HP spadło do 0 lub niżej',
      EMPTY_DECK: 'Przyczyna: pusta talia',
    },
    playAgain: 'Zagraj ponownie',
  },
  cardType: {
    CHARACTER: 'Postać',
    EVENT: 'Wydarzenie',
  },
  card: {
    cost: (n) => `Koszt ${n}`,
    attack: (n) => `Atak ${n}`,
    hp: (n) => `HP ${n}`,
    art: 'Ilustracja',
    trapped: 'Uwięziona',
    duration: (n) => turns(n),
    instant: 'Natychmiast',
    turnsLeft: (n) => `Zostało: ${turns(n)}`,
    faction: {
      developers: 'Developerzy',
      testers: 'Testerzy',
      neutral: 'Neutralne',
    },
  },
};
