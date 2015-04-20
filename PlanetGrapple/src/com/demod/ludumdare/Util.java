package com.demod.ludumdare;

import java.awt.Frame;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;

import org.jbox2d.common.Vec2;

public final class Util {
	public static void debugShowImage(Image infectImage) {
		JDialog dialog = new JDialog((Frame) null);
		dialog.setTitle("DEBUG");
		dialog.setModal(true);
		dialog.getContentPane().add(new JLabel(new ImageIcon(infectImage)));
		dialog.pack();
		dialog.setVisible(true);
	}

	public static Vec2 rotateVector(Vec2 v, float radians) {
		float angle = (float) Math.atan2(v.y, v.x) + radians;
		float length = v.length();
		return new Vec2(length * (float) Math.cos(angle), length
				* (float) Math.sin(angle));

	}

	private Util() {
	}

}
