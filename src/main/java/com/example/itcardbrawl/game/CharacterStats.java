package com.example.itcardbrawl.game;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.example.itcardbrawl.game.Match.Side;

/** Wartości postaci po uwzględnieniu aktywnych wydarzeń obu stron. */
public record CharacterStats(int attack, int health, Set<AbilityKeyword> keywords, boolean trapped) {

	public CharacterStats {
		keywords = Set.copyOf(keywords);
	}

	public static CharacterStats of(BoardCharacter character, Side owner, List<ActiveEvent> playerEvents,
			List<ActiveEvent> opponentEvents) {
		int attack = character.instance().card().attack();
		int health = character.instance().card().health();
		EnumSet<AbilityKeyword> keywords = EnumSet.noneOf(AbilityKeyword.class);
		Ability ability = character.instance().card().ability();
		if (ability != null && !ability.keyword().takesAmount()) {
			keywords.add(ability.keyword());
		}
		boolean trapped = false;
		for (Side eventOwner : Side.values()) {
			List<ActiveEvent> events = eventOwner == Side.PLAYER ? playerEvents : opponentEvents;
			for (ActiveEvent event : events) {
				for (Effect effect : event.instance().card().effects()) {
					if (!effect.kind().modifier() || !applies(effect, event, eventOwner, owner, character)) {
						continue;
					}
					switch (effect.kind()) {
						case ATTACK -> attack += effect.amount();
						case HEALTH -> health += effect.amount();
						case GUARD -> keywords.add(AbilityKeyword.GUARD);
						case RUSH -> keywords.add(AbilityKeyword.RUSH);
						case TRAP -> trapped = true;
						default -> { }
					}
				}
			}
		}
		return new CharacterStats(Math.max(0, attack), health, keywords, trapped);
	}

	private static boolean applies(Effect effect, ActiveEvent event, Side eventOwner, Side characterOwner,
			BoardCharacter character) {
		return switch (effect.scope()) {
			case TARGET -> event.targetId() != null && event.targetId() == character.instance().instanceId();
			case OWN_BOARD -> eventOwner == characterOwner;
			case ENEMY_BOARD -> eventOwner != characterOwner;
			case ALL_BOARDS -> true;
			default -> false;
		};
	}

	public boolean has(AbilityKeyword keyword) {
		return keywords.contains(keyword);
	}

	public int remainingHealth(int damage) {
		return health - damage;
	}
}
