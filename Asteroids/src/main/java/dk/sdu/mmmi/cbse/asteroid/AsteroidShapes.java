package dk.sdu.mmmi.cbse.asteroid;

import java.util.Random;

/**
 * Procedural jagged rock outlines, so asteroids of the same radius don't
 * all render as the same plain square. Visual only - callers keep using
 * their own numeric size as the collision radius.
 */
final class AsteroidShapes {

    private AsteroidShapes() {
    }

    static double[] jaggedPolygon(int baseRadius, Random random) {
        int points = 8 + random.nextInt(4); // 8-11 points
        double[] coordinates = new double[points * 2];
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double jitter = 0.7 + random.nextDouble() * 0.5; // 70%-120% of base radius
            double r = baseRadius * jitter;
            coordinates[i * 2] = Math.cos(angle) * r;
            coordinates[i * 2 + 1] = Math.sin(angle) * r;
        }
        return coordinates;
    }
}
