package com.demod.ludumdare.world;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.util.Collection;
import java.util.Optional;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.joints.DistanceJointDef;
import org.jbox2d.dynamics.joints.Joint;

import com.demod.ludumdare.Engine;
import com.demod.ludumdare.InputState;
import com.demod.ludumdare.Util;

public class Player extends Planet {

	private static final float RADIUS = 1.5f;

	private static final float GRAPPLE_WIDTH = 0.5f;
	@SuppressWarnings("unused")
	private static final float GRAPPLE_ANGULAR_IMPULSE = 1000f;
	private static final float GRAPPLE_FORCE = 1000f;

	private Optional<Planet> grappleTarget = Optional.empty();
	private Joint grappleJoint;

	private long lastInfectSound = System.currentTimeMillis();

	public Player(Canvas canvas, World world) {
		super(canvas, world, RADIUS);
	}

	public void decideGrapple(Engine engine, World world, InputState input,
			Collection<Planet> planets) {
		if (getLatched().size() > 0) {
			if (System.currentTimeMillis() - lastInfectSound > 3000) {
				engine.getSoundPlayerInfected().play(0.25);
				lastInfectSound = System.currentTimeMillis();
			}
		}

		if (grappleTarget.filter(t -> !planets.contains(t)).isPresent()) {
			grappleTarget = Optional.empty();
			world.destroyJoint(grappleJoint);
		}

		if (!input.isMouseDown(MouseEvent.BUTTON1) && grappleTarget.isPresent()) {
			grappleTarget = Optional.empty();
			world.destroyJoint(grappleJoint);
		}

		if (!grappleTarget.isPresent() && input.isMouseDown(MouseEvent.BUTTON1)) {
			Vec2 mouseVector = input.getMouseVector();
			grappleTarget = planets.parallelStream()
					.filter(p -> p != Player.this).filter(p -> {
						Vec2 local = p.getBody().getLocalPoint(mouseVector);
						return drawShape.contains(local.x, local.y);
					}).findAny();
			if (grappleTarget.isPresent()) {
				engine.getSoundGrapple().play(1);
				DistanceJointDef def = new DistanceJointDef();
				Body b2 = grappleTarget.get().getBody();
				def.initialize(body, b2, body.getWorldCenter(),
						b2.getWorldCenter());
				def.collideConnected = false;
				grappleJoint = world.createJoint(def);
			}
		}

		if (grappleTarget.isPresent()) {
			Body b2 = grappleTarget.get().getBody();

			// body.applyAngularImpulse(GRAPPLE_ANGULAR_IMPULSE);
			// b2.applyAngularImpulse(GRAPPLE_ANGULAR_IMPULSE);

			Vec2 delta = b2.getWorldCenter().sub(body.getWorldCenter());
			Vec2 force = Util.rotateVector(delta, (float) (Math.PI / 2.0));
			force.normalize();
			force.mulLocal(GRAPPLE_FORCE);

			b2.applyForceToCenter(force);
			force.negateLocal();
			body.applyForceToCenter(force);
		}
	}

	@Override
	public void destroy(Engine engine) {
		if (grappleTarget.isPresent()) {
			grappleTarget = Optional.empty();
			engine.getWorld().destroyJoint(grappleJoint);
		}
		super.destroy(engine);
		engine.getSoundLose().play(1);
	}

	@Override
	public Color getColor() {
		return Color.cyan.darker();
	}

	public void renderGrapple(Graphics2D g) {
		if (!grappleTarget.isPresent()) {
			return;
		}
		Planet planet = grappleTarget.get();
		Vec2 c1 = body.getWorldCenter();
		Vec2 c2 = planet.getBody().getWorldCenter();

		Stroke ps = g.getStroke();
		try {
			g.setStroke(new BasicStroke(GRAPPLE_WIDTH));
			g.setColor(Color.white);
			g.draw(new Line2D.Float(c1.x, c1.y, c2.x, c2.y));
		} finally {
			g.setStroke(ps);
		}
	}
}
