package dk.sdu.mmmi.cbse.common.asteroids;

public enum AsteroidSize {
    LARGE(14, 1.0),
    MEDIUM(9, 1.4),
    SMALL(5, 1.9);

    private final int radius;
    private final double speedMultiplier;

    AsteroidSize(int radius, double speedMultiplier) {
        this.radius = radius;
        this.speedMultiplier = speedMultiplier;
    }

    public int getRadius() {
        return radius;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    /**
     * The size this asteroid splits into, or null if it should just
     * disappear (SMALL has no smaller size).
     */
    public AsteroidSize smaller() {
        switch (this) {
            case LARGE:
                return MEDIUM;
            case MEDIUM:
                return SMALL;
            default:
                return null;
        }
    }
}
