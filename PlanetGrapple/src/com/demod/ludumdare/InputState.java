package com.demod.ludumdare;

import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

import org.jbox2d.common.Vec2;

import com.google.common.collect.Sets;

public class InputState {
	private final Vec2 mouseVector = new Vec2();
	private final Set<Integer> mouseClick = Sets.newConcurrentHashSet();
	private final Set<Integer> mouseDown = Sets.newConcurrentHashSet();

	public InputState(Canvas canvas, Vec2 gameSize) {
		canvas.setFocusable(true);
		canvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				mouseDown.add(e.getButton());
				mouseClick.add(e.getButton());
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				mouseDown.remove(e.getButton());
			}
		});
		canvas.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				updateMouseVector(e);
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				updateMouseVector(e);
			}

			private void updateMouseVector(MouseEvent e) {
				mouseVector.x = e.getX() * gameSize.x / canvas.getWidth();
				mouseVector.y = e.getY() * gameSize.y / canvas.getHeight();
			}
		});
	}

	public Vec2 getMouseVector() {
		return mouseVector;
	}

	public boolean isMouseClick(int button) {
		return mouseClick.contains(button);
	}

	public boolean isMouseDown(int button) {
		return mouseDown.contains(button);
	}

	public void resetMouseClick(int button) {
		mouseClick.remove(button);
	}
}
