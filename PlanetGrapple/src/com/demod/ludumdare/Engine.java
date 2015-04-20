package com.demod.ludumdare;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import kuusisto.tinysound.Music;
import kuusisto.tinysound.Sound;
import kuusisto.tinysound.TinySound;

import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.shapes.EdgeShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.Filter;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.contacts.Contact;

import com.demod.ludumdare.world.Enemy;
import com.demod.ludumdare.world.GameObject;
import com.demod.ludumdare.world.Planet;
import com.demod.ludumdare.world.Player;
import com.google.common.collect.Sets;

public class Engine {
	public static final int COLLIDE_CANVAS = 1;
	public static final int COLLIDE_PLANET = 2;
	public static final int COLLIDE_ENEMY = 4;
	public static final int COLLIDE_LATCHED = 8;
	public static final float DT = 1f / 120f;
	public static final float GRAVITY_FACTOR = 1.2f;
	public static final float CURVE_FACTOR = 0.0002f;
	private static final float CURVE_SPEED_FACTOR = 1.001f;
	private static final float CURVE_ANGULAR_FACTOR = 0.999f;
	public static final float REPULSION_FACTOR = -0.4f;
	public static final float REPULSION_THRESHOLD_FACTOR = 2f;
	private static final float REPULSION_ANGULAR_GRADIENT = 10f;
	private static final float THRUSTER_FORCE = 0.8f;
	private static final float INFECTED_ANGULAR_FACTOR = 1.001f;
	private static final float DEBUG_DRAG_FORCE_FACTOR = 100f;
	private static final float ENEMY_SPEED_CAP = 24;
	private static final float ENEMY_SPEED_CAP_REDUCTION_FACTOR = 0.9f;
	private static final float PLANET_SPEED_CAP = 100;
	private static final float PLANET_SPEED_CAP_REDUCTION_FACTOR = 0.99f;

	private final InputState input;

	private final Vec2 gameSize;
	private final Random rand = new Random();
	private final World world = new World(new Vec2());
	private Optional<Player> player = Optional.empty();

	private final EnemyHiveMind enemyHiveMind;
	private final Collection<Planet> planets = Sets.newLinkedHashSet();

	private final Collection<Enemy> enemies = Sets.newLinkedHashSet();

	private final Optional<Planet> debugDragTarget = Optional.empty();

	private final Music musicAmbiance;
	private final Sound soundGrapple;

	private final Sound soundEnemyBump;
	private final Sound soundInfect;
	private final Sound soundLose;
	private final Sound soundPlayerInfected;
	private final Sound soundWall;
	private final Sound soundPlanetDead;
	private final Sound soundEnemyDead;
	private final Sound soundEnemyDying;
	private final Sound[] soundPlanetBump;
	private static Object ID_WALL = new Object();

	private long timeStart = -1;
	private long timeEnd = -1;
	private int endPlanets = -1;
	private int endEnemies = -1;

	private boolean begin = true;
	private boolean gameover = false;

	public Engine(Canvas canvas, Vec2 gameSize, InputState input,
			EnemyHiveMind enemyHiveMind) {
		this.gameSize = gameSize;
		this.input = input;
		this.enemyHiveMind = enemyHiveMind;

		TinySound.init();
		musicAmbiance = TinySound.loadMusic(loadURL("sounds/ambiance.wav"));
		musicAmbiance.play(true, 2);

		soundGrapple = TinySound.loadSound(loadURL("sounds/grapple.wav"));
		soundEnemyBump = TinySound.loadSound(loadURL("sounds/enemybump.wav"));
		soundInfect = TinySound.loadSound(loadURL("sounds/infect.wav"));
		soundLose = TinySound.loadSound(loadURL("sounds/lose.wav"));
		soundPlayerInfected = TinySound
				.loadSound(loadURL("sounds/playerInfected.wav"));
		soundWall = TinySound.loadSound(loadURL("sounds/wall.wav"));
		soundPlanetDead = TinySound.loadSound(loadURL("sounds/planetdead.wav"));
		soundEnemyDead = TinySound.loadSound(loadURL("sounds/enemydead.wav"));
		soundEnemyDying = TinySound.loadSound(loadURL("sounds/enemydying.wav"));
		soundPlanetBump = new Sound[] {
				TinySound.loadSound(loadURL("sounds/planetbump1.wav")),
				TinySound.loadSound(loadURL("sounds/planetbump2.wav")),
				TinySound.loadSound(loadURL("sounds/planetbump3.wav")),
				TinySound.loadSound(loadURL("sounds/planetbump4.wav")), };

		createCanvasWalls();

		Player player = new Player(canvas, world);
		player.getBody().setTransform(
				new Vec2(rand.nextFloat() * gameSize.x / 2f, rand.nextFloat()
						* gameSize.y / 2f + gameSize.y / 2f),
				(float) Math.PI * 2 * rand.nextFloat());
		this.player = Optional.of(player);
		planets.add(player);

		// Planets
		for (int x = 0; x < 2; x++) {
			for (int y = 0; y < 2; y++) {
				int count = (x == 1 && y == 0) ? 1 : (x == 0 && y == 1) ? 5
						: 10;
				for (int i = 0; i < count; i++) {
					float radius = Math.round((rand.nextFloat()
							* rand.nextFloat() * 0.5 + 1f) / 0.5f) * 0.5f;
					Planet planet = new Planet(canvas, world, radius);
					Body body = planet.getBody();
					body.setTransform(new Vec2(rand.nextFloat() * gameSize.x
							/ 2f + x * gameSize.x / 2, rand.nextFloat()
							* gameSize.y / 2f + y * gameSize.y / 2),
							(float) Math.PI * 2 * rand.nextFloat());
					body.setAngularVelocity((2 * rand.nextFloat()
							* rand.nextFloat() - 1) * 10);

					float speed = 10;
					body.setLinearVelocity(new Vec2(rand.nextFloat() * speed
							* 2 - speed, rand.nextFloat() * speed * 2 - speed));
					planets.add(planet);
				}
			}
		}

		// Enemies
		for (int i = 0; i < 500; i++) {
			Enemy enemy = new Enemy(enemyHiveMind, world);
			Body body = enemy.getBody();
			body.setTransform(new Vec2(rand.nextFloat() * gameSize.x / 2f
					+ gameSize.x / 2f, rand.nextFloat() * gameSize.y / 2f),
					(float) Math.PI * 2 * rand.nextFloat());
			body.setAngularVelocity((2 * rand.nextFloat() * rand.nextFloat() - 1) * 10);

			float speed = 10;
			body.setLinearVelocity(new Vec2(rand.nextFloat() * speed * 2
					- speed, rand.nextFloat() * speed * 2 - speed));
			enemies.add(enemy);
		}

		world.setContactListener(new ContactListener() {

			@Override
			public void beginContact(Contact contact) {
				Object u1 = contact.m_fixtureA.getUserData();
				Object u2 = contact.m_fixtureB.getUserData();
				if (u1 instanceof Enemy || u2 instanceof Enemy) {
					soundEnemyBump.play(1);
					if (u1 instanceof Enemy) {
						((Enemy) u1).damage(Engine.this, 1);
					} else {
						((Enemy) u2).damage(Engine.this, 1);
					}
				}
				if (u1 == ID_WALL || u2 == ID_WALL) {
					soundWall.play(0.02);
				}
				if (u1 instanceof Planet && u2 instanceof Planet) {
					soundPlanetBump[rand.nextInt(soundPlanetBump.length)]
							.play(0.2);
				}
			}

			@Override
			public void endContact(Contact contact) {
				Object u1 = contact.m_fixtureA.getUserData();
				Object u2 = contact.m_fixtureB.getUserData();
				if (u1 instanceof Enemy && u2 instanceof Planet) {
					onContact((Enemy) u1, (Planet) u2);
				} else if (u1 instanceof Planet && u2 instanceof Enemy) {
					onContact((Enemy) u2, (Planet) u1);
				}
			}

			@Override
			public void postSolve(Contact contact, ContactImpulse impulse) {
			}

			@Override
			public void preSolve(Contact contact, Manifold oldManifold) {
			}
		});
	}

	private void createCanvasWalls() {
		EdgeShape shape = new EdgeShape();
		BodyDef def = new BodyDef();

		Vec2 tl = new Vec2();
		Vec2 tr = new Vec2(gameSize.x, 0);
		Vec2 bl = new Vec2(0, gameSize.y);
		Vec2 br = new Vec2(gameSize.x, gameSize.y);

		Filter filter = new Filter();
		filter.categoryBits = COLLIDE_CANVAS;

		shape.set(tl, tr);
		Body b;
		Fixture f;
		b = world.createBody(def);
		f = b.createFixture(shape, 1);
		f.setUserData(ID_WALL);
		f.setFilterData(filter);
		shape.set(tr, br);
		b = world.createBody(def);
		f = b.createFixture(shape, 1);
		f.setUserData(ID_WALL);
		f.setFilterData(filter);
		shape.set(br, bl);
		b = world.createBody(def);
		f = b.createFixture(shape, 1);
		f.setUserData(ID_WALL);
		f.setFilterData(filter);
		shape.set(bl, tl);
		b = world.createBody(def);
		f = b.createFixture(shape, 1);
		f.setUserData(ID_WALL);
		f.setFilterData(filter);
	}

	private void gameOver() {
		if (gameover) {
			return;
		}
		gameover = true;
		timeEnd = System.currentTimeMillis();
		endEnemies = enemies.size();
		endPlanets = planets.size();
	}

	public int getEndEnemies() {
		return endEnemies;
	}

	public int getEndPlanets() {
		return endPlanets;
	}

	public Collection<Enemy> getEnemies() {
		return enemies;
	}

	public EnemyHiveMind getEnemyHiveMind() {
		return enemyHiveMind;
	}

	public Vec2 getGameSize() {
		return gameSize;
	}

	public Collection<Planet> getPlanets() {
		return planets;
	}

	public Optional<Player> getPlayer() {
		return player;
	}

	public Sound getSoundEnemyBump() {
		return soundEnemyBump;
	}

	public Sound getSoundEnemyDead() {
		return soundEnemyDead;
	}

	public Sound getSoundEnemyDying() {
		return soundEnemyDying;
	}

	public Sound getSoundGrapple() {
		return soundGrapple;
	}

	public Sound getSoundInfect() {
		return soundInfect;
	}

	public Sound getSoundLose() {
		return soundLose;
	}

	public Sound[] getSoundPlanetBump() {
		return soundPlanetBump;
	}

	public Sound getSoundPlanetDead() {
		return soundPlanetDead;
	}

	public Sound getSoundPlayerInfected() {
		return soundPlayerInfected;
	}

	public Sound getSoundWall() {
		return soundWall;
	}

	public long getTimeEnd() {
		return timeEnd;
	}

	public long getTimeStart() {
		return timeStart;
	}

	public World getWorld() {
		return world;
	}

	public boolean isBegin() {
		return begin;
	}

	public boolean isGameover() {
		return gameover;
	}

	private URL loadURL(String path) {
		return Engine.class.getClassLoader().getResource(path);
	}

	private void onContact(Enemy enemy, Planet planet) {
		// planet.infect(1);
		if (!enemy.isLatched()) {
			enemy.latch(this, planet);
		}
	}

	public void renderBegin(Graphics2D g) {
		g.setColor(new Color(64, 0, 0, 240));
		g.fillRect(50, 50, 1024 - 80, 768 - 80);

		g.setColor(Color.white);
		// TODO
		g.setFont(new Font("Courier New", Font.BOLD, 100));
		g.drawString("RED THINGS", 210, 150);
		g.setFont(new Font("Courier New", Font.BOLD, 50));
		g.drawString("Captain!  Wake up!  We", 100, 200);
		g.drawString("believe we encountered ", 100, 250);
		g.drawString("another dimension but this ", 100, 300);
		g.drawString("time things are BAD.  Like ", 100, 350);
		g.drawString("real bad! EVERYONE HAS ", 100, 400);
		g.drawString("TURNED INTO BOXES. Also ", 100, 450);
		g.drawString("some life form is out there", 100, 500);
		g.drawString("and they don't look friendly!", 100, 550);
		g.drawString("THE RED THINGS JUST ATE FRANK!", 70, 650);
		if ((System.currentTimeMillis() / 1000) % 2 == 0) {
			g.drawString("-- DO SOMETHING --", 250, 700);
		}
	}

	public void renderDebug(Graphics2D g) {
		if (debugDragTarget.isPresent()) {
			Planet planet = debugDragTarget.get();
			Vec2 center = planet.getBody().getWorldCenter();
			Vec2 mouse = input.getMouseVector();
			g.setColor(Color.magenta);
			g.draw(new Line2D.Float(center.x, center.y, mouse.x, mouse.y));
		}

		// for (Planet target : enemyHiveMind.getTargetPlanets()) {
		// g.setColor(Color.blue);
		// Vec2 center = target.getBody().getWorldCenter();
		// float r = target.getRadius() * 2;
		// g.draw(new Ellipse2D.Float(center.x - r, center.y - r, r * 2, r *
		// 2));
		// }

		// for (Planet planet : planets) {
		// Vec2 center = planet.getBody().getWorldCenter();
		// float t1 = (float) Math.sqrt(planet.getBody().getMass())
		// * REPULSION_THRESHOLD_FACTOR;
		// g.setColor(Color.magenta);
		// g.draw(new Ellipse2D.Float(center.x - t1, center.y - t1, t1 * 2,
		// t1 * 2));
		// }

	}

	public void renderLost(Graphics2D g) {
		g.setColor(Color.white);
		// TODO
		g.setFont(new Font("Courier New", Font.BOLD, 100));
		g.drawString(player.isPresent() ? " WINNER " : "GAME OVER", 250, 150);
		g.setFont(new Font("Courier New", Font.BOLD, 50));
		g.drawString("Crew: " + getEndPlanets(), 100, 250);
		g.drawString("Red Things: " + getEndEnemies(), 100, 300);
		g.drawString("Time (Seconds): "
				+ ((getTimeEnd() - getTimeStart()) / 1000) + " seconds", 100,
				350);
	}

	public void tick() {
		if (begin) {
			if (input.isMouseClick(MouseEvent.BUTTON1)) {
				input.resetMouseClick(MouseEvent.BUTTON1);
				begin = false;
				timeStart = System.currentTimeMillis();
			}
			return;
		}

		updateForces();

		if (player.filter(GameObject::isDestroyed).isPresent()) {
			player = Optional.empty();
			gameOver();
		}

		if (enemies.isEmpty()) {
			gameOver();
		}

		player.ifPresent(p -> p.decideGrapple(this, world, input, planets));

		// if (input.isMouseDown(MouseEvent.BUTTON3)
		// && !debugDragTarget.isPresent()) {
		// Vec2 mouseVector = input.getMouseVector();
		// debugDragTarget = planets.parallelStream().filter(p -> {
		// Vec2 local = p.getBody().getLocalPoint(mouseVector);
		// return p.getLocalShape().contains(local.x, local.y);
		// }).findAny();
		// }
		//
		// if (debugDragTarget.filter(GameObject::isDestroyed).isPresent()) {
		// debugDragTarget = Optional.empty();
		// }
		//
		// if (input.isMouseDown(MouseEvent.BUTTON3)) {
		// if (debugDragTarget.isPresent()) {
		// Planet planet = debugDragTarget.get();
		// Body body = planet.getBody();
		// Vec2 delta = input.getMouseVector().sub(body.getWorldCenter());
		// body.m_force.setZero();
		// body.applyForceToCenter(delta.mul(DEBUG_DRAG_FORCE_FACTOR));
		// System.out.println(body.getLinearVelocity().length());
		// }
		// } else {
		// debugDragTarget = Optional.empty();
		// }

		enemies.removeIf(e -> {
			if (e.decideIfDead()) {
				e.destroy(this);
				return true;
			}
			return false;
		});

		planets.removeIf(p -> {
			if (p.decideIfDead(world, Engine.this)) {
				p.destroy(Engine.this);
				if (player.filter(p2 -> p == p2).isPresent()) {
					player = Optional.empty();
					gameOver();
				}
				return true;
			}
			return false;
		});

		planets.parallelStream().forEach(Planet::checkLatchedValid);

		world.step(DT, 10, 10);

		enemies.parallelStream().forEach(e -> e.checkForDamage(Engine.this));
		enemies.stream().filter(Enemy::isLatched).forEach(Enemy::updateLatch);
		enemies.stream().filter(Enemy::isLatched)
				.forEach(Enemy::checkForInfect);
	}

	private void updateForces() {
		updateForces_gravity();
		updateForces_curve();
		updateForces_repulsion();
		updateForces_thrusters();
		updateForces_infection();
		updateForces_speedCaps();
	}

	private void updateForces_curve() {
		// "Curve ball" effect
		planets.parallelStream()
				.map(Planet::getBody)
				.forEach(
						b -> {
							Vec2 d = b.getLinearVelocity();
							float a = b.getAngularVelocity();
							float newAngle = (float) (Math.atan2(d.y, d.x) + a
									* CURVE_FACTOR);
							Vec2 newDelta = new Vec2(
									(float) Math.cos(newAngle), (float) Math
											.sin(newAngle)).mul(d.length())
									.mul(CURVE_SPEED_FACTOR);
							b.setLinearVelocity(newDelta);
							b.setAngularVelocity(a * CURVE_ANGULAR_FACTOR);
						});
	}

	private void updateForces_gravity() {
		// Gravitational Pull
		planets.parallelStream()
				.map(Planet::getBody)
				.forEach(
						b1 -> {
							Stream.concat(
									planets.parallelStream(),
									enemies.parallelStream().filter(
											e -> !e.isLatched()))
									.map(GameObject::getBody)
									.filter(b2 -> b1 != b2)
									.forEach(
											b2 -> {
												Vec2 delta = b2.getPosition()
														.sub(b1.getPosition());

												float force = GRAVITY_FACTOR
														* (b2.getMass() * b1
																.getMass())
														/ (float) Math.pow(
																delta.length(),
																2);

												Vec2 forceVector = delta
														.negate().mul(force);
												b2.applyForce(forceVector,
														b2.getWorldCenter());
											});
						});
	}

	private void updateForces_infection() {
		planets.parallelStream()
				.filter(Planet::isInfected)
				.map(Planet::getBody)
				.forEach(
						b -> {
							b.setAngularVelocity(b.getAngularVelocity()
									* INFECTED_ANGULAR_FACTOR);
						});
	}

	private void updateForces_repulsion() {
		// Repulsion Effect
		planets.parallelStream()
				.map(Planet::getBody)
				.forEach(
						b1 -> {
							Stream.concat(
									planets.parallelStream(),
									enemies.parallelStream().filter(
											e -> !e.isLatched()))
									.map(GameObject::getBody)
									.filter(b2 -> b1 != b2)
									.forEach(
											b2 -> {
												float t1 = (float) Math.sqrt(b1
														.getMass())
														* REPULSION_THRESHOLD_FACTOR;
												float t2 = (float) Math.sqrt(b2
														.getMass())
														* REPULSION_THRESHOLD_FACTOR;
												Vec2 delta = b2.getPosition()
														.sub(b1.getPosition());

												if (delta.length() <= t1 + t2) {
													float force = REPULSION_FACTOR
															* (b2.getMass() * b1
																	.getMass())
															/ delta.length();
													Vec2 forceVector = delta
															.negate()
															.mul(force);

													float av1 = b1
															.getAngularVelocity();
													float rotate = -((float) Math.PI / 2f)
															* (Math.signum(av1) * Math
																	.min(1f,
																			av1
																					/ REPULSION_ANGULAR_GRADIENT));
													forceVector = Util
															.rotateVector(
																	forceVector,
																	rotate);

													b2.applyForce(forceVector,
															b2.getWorldCenter());
												}
											});
						});
	}

	private void updateForces_speedCaps() {
		enemies.parallelStream().map(GameObject::getBody).forEach(b -> {
			Vec2 v = new Vec2(b.getLinearVelocity());
			float speed = v.length();
			if (speed > ENEMY_SPEED_CAP) {
				b.setLinearVelocity(v.mul(ENEMY_SPEED_CAP_REDUCTION_FACTOR));
			}
		});
		planets.parallelStream().map(GameObject::getBody).forEach(b -> {
			Vec2 v = new Vec2(b.getLinearVelocity());
			float speed = v.length();
			if (speed > PLANET_SPEED_CAP) {
				b.setLinearVelocity(v.mul(PLANET_SPEED_CAP_REDUCTION_FACTOR));
			}
		});
	}

	private void updateForces_thrusters() {
		enemyHiveMind.decideTargets(this);

		enemies.parallelStream().filter(e -> !e.isLatched()).forEach(e -> {
			Vec2 thrust = e.decideThrust(this);
			thrust = thrust.mul(THRUSTER_FORCE);
			Body b = e.getBody();
			b.applyForce(thrust, b.getWorldCenter());
		});
	}
}
