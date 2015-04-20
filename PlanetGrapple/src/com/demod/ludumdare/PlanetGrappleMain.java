package com.demod.ludumdare;

import java.awt.Canvas;
import java.awt.Dimension;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;

import org.jbox2d.common.Vec2;

import com.google.common.util.concurrent.AbstractScheduledService;

public class PlanetGrappleMain {

	public static void main(String[] args) {
		Dimension dim = new Dimension(1024, 768);

		JFrame frame = new JFrame("Red Things!");
		Canvas canvas = new Canvas();
		canvas.setPreferredSize(dim);
		frame.getContentPane().add(canvas);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		frame.setResizable(false);
		frame.setVisible(true);

		// Wait for the ability to draw, to minimize conditions later
		while (canvas.getBufferStrategy() == null) {
			canvas.createBufferStrategy(2);
			Thread.yield();
		}

		Vec2 gameSize = new Vec2(dim.width / 10f, dim.height / 10f);
		InputState input = new InputState(canvas, gameSize);
		EnemyHiveMind enemyHiveMind = new EnemyHiveMind();
		Engine engine = new Engine(canvas, gameSize, input, enemyHiveMind);
		Renderer renderer = new Renderer(canvas, gameSize, engine);

		new AbstractScheduledService() {
			@Override
			protected void runOneIteration() throws Exception {
				try {
					engine.tick();
					renderer.render();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			protected Scheduler scheduler() {
				return Scheduler.newFixedRateSchedule(1000000, 1000000 / 60,
						TimeUnit.MICROSECONDS);
			}
		}.startAsync();
	}
}
