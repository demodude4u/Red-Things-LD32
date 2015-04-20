package com.demod.ludumdare.world;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.Optional;
import java.util.Random;

import javafx.util.Pair;

import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.Filter;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.dynamics.World;

import com.demod.ludumdare.EnemyHiveMind;
import com.demod.ludumdare.Engine;
import com.demod.ludumdare.Util;
import com.google.common.base.Preconditions;

public class Enemy extends GameObject {
	public static final float LINEAR_DAMPENING = 0.4f;
	public static final float DENSITY = 0.1f;
	public static final float RADIUS = 0.1f;

	public static final int HEALTH_MAX = 100;

	public static final int DYING_TICKS_MAX = 120;
	public static final float DYING_ANGULAR_FACTOR = 0.05f;
	public static final float DYING_THRUST_SPEED = 2;
	public static final float DYING_JERK_FACTOR = 10f;

	public static final float ACCEL_THRESHOLD = 30;
	public static final int ACCEL_THRESHOLD_DAMAGE = 50;
	public static final float ACCEL_DAMAGE_FACTOR = 1f;
	public static final float ACCEL_LATCHED_FACTOR = 1f;
	public static final float ACCEL_LATCHED_BOOST = 10;

	public static final float THRUST_REPULSION_RADIUS = 3;
	public static final float THRUST_REPULSION_ANGULAR_FACTOR = 1.5f;
	public static final float THRUST_REPULSION_LINEAR_FACTOR = 0.2f;
	public static final float THRUST_BOOST_SPEED = 2;
	public static final float THRUST_WALL_AVOIDANCE = 5;

	public static final int VISIBLE_DAMAGE_TICKS_MAX = 60;

	public static final float INFECT_FACTOR = 0.1f;

	private static final Rectangle2D.Float drawShape = new Rectangle2D.Float(
			-1, -1, 2, 2);
	private static final Shape dyingShape = new Polygon(new int[] { -2, -1, 0,
			1, 2, 1, 0, -1 }, new int[] { 0, 1, 2, 1, 0, -1, -2, -1 }, 8);

	private static final Random rand = new Random();

	private final Body body;

	private int health = HEALTH_MAX;

	private boolean dying = false;
	private int dyingTicks = DYING_TICKS_MAX;

	private Optional<Pair<Planet, Vec2>> latchPoint = Optional.empty();

	private final Filter filter;
	private final Filter latchedFilter;

	private int visibleDamageTicks = 0;
	private boolean hasLastVelocity = false;
	private final Vec2 lastVelocity = new Vec2();
	private float dyingJerk = 0;

	private final EnemyHiveMind hiveMind;

	public Enemy(EnemyHiveMind hiveMind, World world) {
		this.hiveMind = hiveMind;

		PolygonShape shape = new PolygonShape();
		shape.setAsBox(RADIUS, RADIUS);

		BodyDef def = new BodyDef();
		def.allowSleep = false;
		def.linearDamping = LINEAR_DAMPENING;

		body = world.createBody(def);
		body.setType(BodyType.DYNAMIC);
		Fixture fixture = body.createFixture(shape, DENSITY);
		fixture.setFriction(0f);
		fixture.setRestitution(0.5f);
		fixture.setUserData(this);

		filter = new Filter();
		filter.categoryBits = Engine.COLLIDE_ENEMY;
		filter.maskBits ^= Engine.COLLIDE_ENEMY;
		fixture.setFilterData(filter);

		latchedFilter = new Filter();
		latchedFilter.categoryBits = Engine.COLLIDE_LATCHED;
		latchedFilter.maskBits = 0;

	}

	public void checkForDamage(Engine engine) {
		Vec2 v;
		if (isLatched()) {
			Pair<Planet, Vec2> t = latchPoint.get();
			v = t.getKey().getBody()
					.getLinearVelocityFromLocalPoint(t.getValue());
		} else {
			v = body.getLinearVelocity();
		}

		if (hasLastVelocity) {
			Vec2 a = v.sub(lastVelocity);

			float threshold = ACCEL_THRESHOLD;
			if (isLatched() && rand.nextFloat() >= ACCEL_LATCHED_FACTOR) {
				threshold *= ACCEL_LATCHED_BOOST;
			}
			if (a.length() >= threshold) {
				damage(engine, ACCEL_THRESHOLD_DAMAGE);

				int extraDamage = (int) (a.length() * ACCEL_DAMAGE_FACTOR);
				if (extraDamage > 0) {
					damage(engine, extraDamage);
				}

				visibleDamageTicks = VISIBLE_DAMAGE_TICKS_MAX;
			}
		}

		lastVelocity.set(v);
		hasLastVelocity = true;
	}

	public void checkForInfect() {
		if (rand.nextFloat() <= INFECT_FACTOR) {
			latchPoint.get().getKey().infect(1);
		}
	}

	public void damage(Engine engine, int points) {
		health -= points;
		if (health <= 0) {
			dying = true;
			engine.getSoundEnemyDying().play(0.1);
			if (isLatched()) {
				unlatch();
			}
		}
	}

	public boolean decideIfDead() {
		if (!dying) {
			return false;
		}
		if (dyingTicks <= 0) {
			return true;
		}
		dyingTicks--;
		return false;
	}

	public Vec2 decideThrust(Engine engine) {
		Collection<Planet> planets = engine.getPlanets();
		Vec2 gameSize = engine.getGameSize();

		if (dying) {
			dyingJerk += (rand.nextFloat() * 2 - 1) * DYING_JERK_FACTOR;
			body.setAngularVelocity(body.getAngularVelocity() + dyingJerk);

			Vec2 thrust = Util
					.rotateVector(body.getLinearVelocity(),
							Math.signum(body.getAngularVelocity())
									* DYING_ANGULAR_FACTOR
									* (1 - (float) dyingTicks
											/ (float) DYING_TICKS_MAX));
			thrust.normalize();
			return thrust.mul(DYING_THRUST_SPEED).mul(
					1 - (float) dyingTicks / (float) DYING_TICKS_MAX);
		}

		Optional<Pair<Planet, Vec2>> closestTarget = hiveMind
				.getTargetPlanets()
				.parallelStream()
				.map(p -> new Pair<>(p, p.getBody().getWorldCenter()
						.sub(body.getWorldCenter())))
				.sorted((t1, t2) -> Float.compare(
						t1.getValue().lengthSquared(), t2.getValue()
								.lengthSquared())).findFirst();
		Vec2 toTarget = closestTarget.map(Pair::getKey)
				.map(GameObject::getBody)
				.map(b -> b.getWorldCenter().sub(body.getWorldCenter()))
				.orElseGet(Vec2::new);
		toTarget.normalize();

		float speed = planets
				.parallelStream()
				.anyMatch(
						p -> {
							Body b2 = p.getBody();
							Vec2 pos = b2.getPosition().add(
									b2.getLinearVelocity().mul(
											THRUST_REPULSION_LINEAR_FACTOR));
							Vec2 delta = body.getPosition().sub(pos);
							return (delta.length() <= (THRUST_REPULSION_RADIUS + p
									.getRadius())
									+ (Math.abs(b2.getAngularVelocity()) * THRUST_REPULSION_ANGULAR_FACTOR));
						}) ? THRUST_BOOST_SPEED : 1;

		// Planet Avoidance
		Vec2 toAvoidPlanet = null;
		boolean avoidingPlanet = false;
		float closestPlanetDistance = -1;
		if (speed > 1) {
			Optional<Pair<Planet, Vec2>> closest = planets
					.parallelStream()
					.map(p -> new Pair<>(p, p.getBody().getWorldCenter()
							.sub(body.getWorldCenter())))
					.sorted((t1, t2) -> Float.compare(t1.getValue()
							.lengthSquared(), t2.getValue().lengthSquared()))
					.findFirst();

			if (closest.isPresent()) {
				avoidingPlanet = true;
				closestPlanetDistance = closest.get().getKey().getBody()
						.getWorldCenter().sub(body.getWorldCenter()).length();
				toAvoidPlanet = closest.map(Pair::getValue).map(v -> {
					v.normalize();
					return v.negateLocal();
				}).get();
			}
		}

		// Wall Avoidance
		Vec2 center = body.getWorldCenter();
		Vec2 toAvoidWall = new Vec2();
		boolean avoidingWall = false;
		float closestWallDistance = -1;
		if (center.x <= THRUST_WALL_AVOIDANCE) {
			toAvoidWall.set(1, 0);
			avoidingWall = true;
			closestWallDistance = center.x;
		} else if (center.x >= gameSize.x - THRUST_WALL_AVOIDANCE) {
			toAvoidWall.set(-1, 0);
			avoidingWall = true;
			closestWallDistance = gameSize.x - center.x;
		}
		if (center.y <= THRUST_WALL_AVOIDANCE) {
			if (avoidingWall) {
				toAvoidWall.addLocal(0, 1).mulLocal(0.5f);
				closestWallDistance = Math.min(closestWallDistance, center.y);
			} else {
				toAvoidWall.set(0, 1);
				avoidingWall = true;
				closestWallDistance = center.y;
			}
		} else if (center.y >= gameSize.y - THRUST_WALL_AVOIDANCE) {
			if (avoidingWall) {
				toAvoidWall.addLocal(0, -1).mulLocal(0.5f);
				closestWallDistance = Math.min(closestWallDistance, gameSize.y
						- center.y);
			} else {
				toAvoidWall.set(0, -1);
				avoidingWall = true;
				closestWallDistance = gameSize.y - center.y;
			}
		}

		// Choose what to avoid
		Vec2 toAvoid = toTarget;// If I don't pick what to avoid, just do target
		if (avoidingPlanet && avoidingWall) {
			toAvoid = closestPlanetDistance < closestWallDistance ? toAvoidPlanet
					: toAvoidWall;
		} else if (avoidingPlanet) {
			toAvoid = toAvoidPlanet;
		} else if (avoidingWall) {
			toAvoid = toAvoidWall;
		}

		return toAvoid.add(toAvoid).add(toTarget).mul(1f / 3f).mul(speed);
	}

	@Override
	public void destroy(Engine engine) {
		super.destroy(engine);
		engine.getSoundEnemyDead().play(0.1);
	}

	@Override
	public Body getBody() {
		return body;
	}

	public boolean isLatched() {
		return latchPoint.isPresent();
	}

	public void latch(Engine engine, Planet planet) {
		Preconditions.checkState(!isLatched());

		body.getFixtureList().setFilterData(latchedFilter);

		Vec2 local = planet.getBody().getLocalPoint(body.getWorldCenter());
		float r = planet.getRadius();
		local.normalize();

		// http://stackoverflow.com/a/22520995
		float angle = (float) Math.atan2(local.y, local.x);
		local.mulLocal((float) Math.min(1.0 / Math.abs(Math.cos(angle)),
				1.0 / Math.abs(Math.sin(angle))));

		local.mulLocal(r).mulLocal(1.1f);
		latchPoint = Optional.of(new Pair<>(planet, local));
		planet.addLatchedEnemy(engine, this);

		hasLastVelocity = false;
	}

	public void render(Graphics2D g) {
		if (visibleDamageTicks > 0) {
			visibleDamageTicks--;
		}

		Vec2 center = body.getWorldCenter();
		if (isLatched()) {
			Vec2 target = latchPoint.get().getKey().getBody().getWorldCenter();
			g.setColor(rand.nextFloat() < 0.25 ? new Color(0, 0, 0, 255)
					: new Color(128, 0, 0, 172));
			g.draw(new Line2D.Float(center.x, center.y, target.x, target.y));
		}

		AffineTransform pat = g.getTransform();
		try {
			g.translate(center.x, center.y);
			g.scale(1 / 10f, 1 / 10f);

			g.setColor(dying ? (rand.nextFloat() < (float) dyingTicks
					/ (float) DYING_TICKS_MAX) ? Color.red
					: rand.nextBoolean() ? Color.white : Color.lightGray
					: visibleDamageTicks > 0 ? Color.green : isLatched() ? rand
							.nextBoolean() ? Color.pink : Color.red : Color.red);
			g.fill(dying ? dyingShape : drawShape);
		} finally {
			g.setTransform(pat);
		}

	}

	public void unlatch() {
		Optional.ofNullable(body.getFixtureList()).ifPresent(
				f -> f.setFilterData(filter));
		Planet planet = latchPoint.get().getKey();
		planet.removeLatchedEnemy(this);
		body.setLinearVelocity(body.getLinearVelocityFromLocalPoint(
				latchPoint.get().getValue()).mulLocal(2));
		body.setAngularVelocity(body.getAngularVelocity());
		hasLastVelocity = false;
		latchPoint = Optional.empty();
	}

	public void updateLatch() {
		if (dying || latchPoint.get().getKey().isDestroyed()) {
			unlatch();
			return;
		}

		Pair<Planet, Vec2> t = latchPoint.get();
		Vec2 pos = t.getKey().getBody().getWorldPoint(t.getValue());
		body.setTransform(pos, 0);
	}

}
