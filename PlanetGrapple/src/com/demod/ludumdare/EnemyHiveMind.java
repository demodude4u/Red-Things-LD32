package com.demod.ludumdare;

import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import javafx.util.Pair;

import org.jbox2d.common.Vec2;

import com.demod.ludumdare.world.Planet;
import com.google.common.collect.Sets;

public class EnemyHiveMind {

	private final Set<Planet> targetPlanets = Sets.newLinkedHashSet();

	private final Rectangle2D.Float quadRect = new Rectangle2D.Float();

	public void decideTargets(Engine engine) {
		Vec2 gameSize = engine.getGameSize();

		targetPlanets.clear();

		float quadWidth = gameSize.x / 2f;
		float quadHeight = gameSize.y / 2f;
		Vec2 quadCenter = new Vec2();
		// Pick target planets in each quadrant except the one the player is in
		for (int x = 0; x < 2; x++) {
			for (int y = 0; y < 2; y++) {
				quadRect.setFrame(quadWidth * x, quadHeight * y, quadWidth,
						quadHeight);
				quadCenter.set((float) quadRect.getCenterX(),
						(float) quadRect.getCenterY());

				if (engine.getPlayer().filter(p -> {
					Vec2 center = p.getBody().getWorldCenter();
					return quadRect.contains(center.x, center.y);
				}).isPresent()) {
					continue;
				}

				Optional<Pair<Planet, Vec2>> target = engine
						.getPlanets()
						.parallelStream()
						.map(p -> new Pair<>(p, p.getBody().getWorldCenter()
								.sub(quadCenter)))
						.sorted((t1, t2) -> Float
								.compare(t1.getValue().lengthSquared(), t2
										.getValue().lengthSquared()))
						.findFirst();

				target.map(Pair::getKey).ifPresent(targetPlanets::add);
			}
		}
	}

	public Collection<Planet> getTargetPlanets() {
		return targetPlanets;
	}
}
