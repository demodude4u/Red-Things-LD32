package com.demod.ludumdare.world;

import org.jbox2d.dynamics.Body;

import com.demod.ludumdare.Engine;
import com.google.common.base.Preconditions;

public abstract class GameObject {
	private boolean destroyed = false;

	public void destroy(Engine engine) {
		Preconditions.checkState(!destroyed);
		engine.getWorld().destroyBody(getBody());
		destroyed = true;
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	public abstract Body getBody();
}
