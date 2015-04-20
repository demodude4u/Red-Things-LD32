package com.demod.ludumdare;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferStrategy;
import java.awt.image.VolatileImage;

import org.jbox2d.common.Vec2;

public class Renderer {
	public static final int PIXEL_FACTOR = 2;

	private final Canvas canvas;
	private final Vec2 gameSize;
	private final Engine engine;

	private final VolatileImage gameBuf;

	private long timestamp = System.currentTimeMillis();

	public Renderer(Canvas canvas, Vec2 gameSize, Engine engine) {
		this.canvas = canvas;
		this.gameSize = gameSize;
		this.engine = engine;

		int gameBufWidth = canvas.getWidth() / PIXEL_FACTOR;
		int gameBufHeight = canvas.getHeight() / PIXEL_FACTOR;
		gameBuf = canvas.createVolatileImage(gameBufWidth, gameBufHeight);
	}

	public void render() {
		BufferStrategy strategy = canvas.getBufferStrategy();
		Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
		try {
			renderGameBuf();

			g.setRenderingHint(RenderingHints.KEY_RENDERING,
					RenderingHints.VALUE_RENDER_SPEED);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(gameBuf, 0, 0, canvas.getWidth(), canvas.getHeight(),
					null);

			int timestampDelta = (int) (System.currentTimeMillis() - timestamp);
			int fps = 1000 / Math.max(1, timestampDelta);
			timestamp += timestampDelta;
			g.setColor(Color.white);
			g.drawString("FPS: " + fps, 10, 20);
			g.drawString(
					"Time: "
							+ ((System.currentTimeMillis() - engine
									.getTimeStart()) / 1000) + "s", 10, 50);
			g.drawString("Crew: " + engine.getPlanets().size(), 10, 60);
			g.drawString("Red Things: " + engine.getEnemies().size(), 10, 70);

			if (engine.isBegin()) {
				engine.renderBegin(g);
			} else if (engine.isGameover()) {
				engine.renderLost(g);
			}

		} finally {
			g.dispose();
			strategy.show();
		}
	}

	private void renderGameBuf() {
		Graphics2D g = gameBuf.createGraphics();
		try {
			g.scale(gameBuf.getWidth() / gameSize.x, gameBuf.getHeight()
					/ gameSize.y);

			g.setRenderingHint(RenderingHints.KEY_RENDERING,
					RenderingHints.VALUE_RENDER_SPEED);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

			g.setStroke(new BasicStroke(0.1f));

			g.setColor(Color.black);
			g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

			engine.getPlanets().stream().forEach(p -> {
				p.render(g);
			});

			engine.getEnemies().stream().forEach(e -> {
				e.render(g);
			});

			engine.getPlayer().ifPresent(p -> p.renderGrapple(g));

			engine.renderDebug(g);
		} finally {
			g.dispose();
		}
	}

}
