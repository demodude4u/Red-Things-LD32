package com.demod.ludumdare.world;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.util.Collection;
import java.util.Random;
import java.util.stream.IntStream;

import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.Filter;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.dynamics.World;

import com.demod.ludumdare.Engine;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class Planet extends GameObject {
	private static final float RESTITUTION = 0.5f;
	private static final float FRICTION = 0.01f;
	public static final float DENSITY = 2;
	private static final float INFECT_FACTOR = 2;
	private static final float INFECT_SPAWN_FACTOR = 0.2f;
	private static final float INFECTED_ANIMATION_FACTOR = 0.1f;

	protected final Body body;

	private int infectPoints;
	private final int infectThreshold;

	private final int infectSize;
	private final int infectMax;
	private final byte[] infectGrid;
	private final MemoryImageSource infectMIS;
	private final Image infectImage;

	protected final Rectangle2D.Float drawShape;
	private final float radius;

	private final Random rand = new Random();

	private final Collection<Enemy> latched = Sets.newLinkedHashSet();

	public Planet(Canvas canvas, World world, float radius) {
		this.radius = radius;

		PolygonShape shape = new PolygonShape();
		shape.setAsBox(radius, radius);

		BodyDef def = new BodyDef();
		def.allowSleep = false;

		body = world.createBody(def);
		body.setType(BodyType.DYNAMIC);
		Fixture fixture = body.createFixture(shape, DENSITY);
		fixture.setFriction(FRICTION);
		fixture.setRestitution(RESTITUTION);
		fixture.setUserData(this);

		Filter filter = new Filter();
		filter.categoryBits = Engine.COLLIDE_PLANET;
		fixture.setFilterData(filter);

		drawShape = new Rectangle2D.Float(-radius, -radius, radius * 2,
				radius * 2);

		infectSize = (int) (radius * 10);
		infectGrid = new byte[infectSize * infectSize];
		int[] cmap = IntStream.of(//
				0x00000000,//
				0x88FF0000,//
				0xFFFF0000,//
				0xFFBB0000,//
				0xFF330000,//
				0xFF000000//
				).toArray();
		infectMax = cmap.length - 1;
		ColorModel cm = new IndexColorModel(8, cmap.length, cmap, 0,
				DataBuffer.TYPE_BYTE, null);
		infectMIS = new MemoryImageSource(infectSize, infectSize, cm,
				infectGrid, 0, infectSize);
		infectMIS.setAnimated(true);
		infectMIS.setFullBufferUpdates(true);
		infectImage = canvas.createImage(infectMIS);
		infectMIS.newPixels();

		infectPoints = 0;
		infectThreshold = (int) (infectGrid.length * INFECT_FACTOR);
	}

	public void addLatchedEnemy(Engine engine, Enemy e) {
		if (latched.isEmpty()) {
			engine.getSoundInfect().play(0.5);
		}
		latched.add(e);

	}

	public void checkLatchedValid() {
		latched.removeIf(GameObject::isDestroyed);
	}

	public boolean decideIfDead(World world, Engine engine) {
		if (infectPoints < infectThreshold) {
			return false;
		}

		ImmutableList.copyOf(latched).stream().filter(Enemy::isLatched)
				.forEach(Enemy::unlatch);

		Vec2 pos = new Vec2();
		int enemyCount = (int) (radius * radius * 4 * INFECT_SPAWN_FACTOR);
		for (int i = 0; i < enemyCount; i++) {
			pos.set(rand.nextFloat() * radius * 2 - radius, rand.nextFloat()
					* radius * 2 - radius);
			Enemy enemy = new Enemy(engine.getEnemyHiveMind(), world);
			Body b = enemy.getBody();
			b.setTransform(body.getWorldPoint(pos), body.getAngle());
			b.setLinearVelocity(body.getLinearVelocityFromLocalPoint(pos)
					.mulLocal(2));
			b.setAngularVelocity(body.getAngularVelocity());
			engine.getEnemies().add(enemy);
		}

		return true;
	}

	@Override
	public void destroy(Engine engine) {
		super.destroy(engine);
		engine.getSoundPlanetDead().play(0.8);
	}

	@Override
	public Body getBody() {
		return body;
	}

	public Color getColor() {
		return Color.orange;
	}

	public Collection<Enemy> getLatched() {
		return latched;
	}

	public Rectangle2D.Float getLocalShape() {
		return drawShape;
	}

	public float getRadius() {
		return radius;
	}

	public void infect(int points) {
		infectPoints += points;
		for (int i = 0; i < points; i++) {
			int idx = rand.nextInt(infectGrid.length);
			if (infectGrid[idx] < infectMax) {
				infectGrid[idx]++;
			}
		}
	}

	public boolean isInfected() {
		return infectPoints > 0;
	}

	public void removeLatchedEnemy(Enemy e) {
		latched.remove(e);
	}

	public void render(Graphics2D g) {
		if (isInfected()) {
			int switchCount = (int) (infectPoints * INFECTED_ANIMATION_FACTOR);
			for (int i = 0; i < switchCount; i++) {
				int idx = rand.nextInt(infectGrid.length);
				if (infectGrid[idx] > 0) {
					infectGrid[idx]--;
					infectGrid[rand.nextInt(infectGrid.length)]++;
				}
			}
			infectMIS.newPixels();
		}

		Vec2 center = body.getWorldCenter();

		AffineTransform pat = g.getTransform();
		try {
			g.translate(center.x, center.y);
			g.rotate(body.getAngle());

			g.setColor(!latched.isEmpty() ? (rand.nextFloat() > 0.1f) ? getColor()
					: getColor().brighter()
					: getColor());
			g.fill(drawShape);

			if (isInfected()) {
				g.scale(radius, radius);
				g.drawImage(infectImage, -1, -1, 2, 2, null);
			}

			if (!latched.isEmpty()) {
				g.setColor(((System.currentTimeMillis() / 1000) % 2 == 0) ? Color.red
						: getColor());
				float scale = (radius + 1) / radius;
				g.scale(scale, scale);
				g.draw(drawShape);
			}

		} finally {
			g.setTransform(pat);
		}

		// {
		// Vec2 pos = body.getPosition().add(
		// body.getLinearVelocity().mul(
		// Enemy.THRUST_REPULSION_LINEAR_FACTOR));
		// float r = (Enemy.THRUST_REPULSION_RADIUS + radius)
		// + (Math.abs(body.getAngularVelocity()) *
		// Enemy.THRUST_REPULSION_ANGULAR_FACTOR);
		// g.setColor(Color.white);
		// g.draw(new Line2D.Float(center.x, center.y, pos.x, pos.y));
		// g.draw(new Ellipse2D.Float(pos.x - r, pos.y - r, r * 2, r * 2));
		// }
	}
}
