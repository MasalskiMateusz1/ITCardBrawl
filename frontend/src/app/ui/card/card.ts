import { Component, computed, inject, input, signal } from '@angular/core';
import { CardType } from '../../game/match';
import { LanguageService } from '../../i18n/language.service';

export type CardVariant = 'full' | 'compact';

/**
 * The shared card frame for characters and events (hand, board, active events, preview, graveyard).
 * Presentational only: the view wraps it in a button and handles clicks. Faction colours come from
 * the `data-faction` host attribute and the `--faction-*` tokens.
 */
@Component({
  imports: [],
  selector: 'app-card',
  styleUrl: './card.css',
  templateUrl: './card.html',
  host: { '[attr.data-faction]': 'faction()', '[class.compact]': 'compact()' },
})
export class Card {
  private readonly languageService = inject(LanguageService);

  readonly variant = input<CardVariant>('full');
  readonly cardId = input.required<string>();
  readonly faction = input.required<string>();
  readonly type = input.required<CardType>();
  readonly name = input.required<string>();
  readonly cost = input.required<number>();
  /** Effective attack. */
  readonly attack = input<number | null>();
  /** Displayed health: remaining HP. */
  readonly health = input<number | null>();
  readonly baseAttack = input<number | null>();
  readonly baseHealth = input<number | null>();
  readonly damaged = input(false);
  /** Already localized keyword name. */
  readonly keyword = input<string | null>();
  /** Already localized ability text. */
  readonly ability = input<string | null>();
  readonly text = input<string | null>();
  /** Already localized effect texts. */
  readonly effects = input<string[]>([]);
  /** Event duration in turns; `0` = instant. */
  readonly duration = input<number | null>();
  /** Turns left of an active event; shown instead of the duration. */
  readonly turnsLeft = input<number | null>();
  readonly selected = input(false);
  readonly playable = input(true);
  readonly trapped = input(false);

  protected readonly t = computed(() => this.languageService.texts().card);
  protected readonly compact = computed(() => this.variant() === 'compact');
  protected readonly isCharacter = computed(() => this.type() === 'CHARACTER');

  /** The only place that builds a card-art URL (AGENTS.md, UI). */
  protected readonly artUrl = computed(() => `/media/cards/${this.cardId()}.webp`);
  /** The cardId whose image failed; a different cardId gets a fresh attempt. */
  private readonly failedCardId = signal<string | null>(null);
  protected readonly artFailed = computed(() => this.failedCardId() === this.cardId());

  protected readonly attackModified = computed(() => {
    const base = this.baseAttack();
    return base != null && this.attack() != null && this.attack() !== base;
  });
  protected readonly healthMarked = computed(() => {
    const base = this.baseHealth();
    return this.damaged() || (base != null && this.health() != null && this.health() !== base);
  });

  protected readonly typeLine = computed(() => {
    const texts = this.languageService.texts();
    const type = texts.cardType[this.type()];
    const faction = texts.card.faction[this.faction()];
    return faction ? `${type} · ${faction}` : type;
  });

  /** Event time label: turns left, else duration, else instant for `duration 0`. */
  protected readonly timeLabel = computed(() => {
    if (this.isCharacter()) {
      return null;
    }
    const t = this.t();
    const left = this.turnsLeft();
    if (left != null) {
      return t.turnsLeft(left);
    }
    const duration = this.duration();
    if (duration == null) {
      return null;
    }
    return duration === 0 ? t.instant : t.duration(duration);
  });

  /** Compact line, in priority order: trapped, keyword, event time, card type. */
  protected readonly compactLine = computed(
    () =>
      (this.trapped() && this.t().trapped) ||
      this.keyword() ||
      this.timeLabel() ||
      this.languageService.texts().cardType[this.type()],
  );

  protected onArtError(): void {
    this.failedCardId.set(this.cardId());
  }
}
