package dk.sdu.mmmi.cbse.common.data;

import java.io.Serializable;
import java.util.UUID;

public class Entity implements Serializable {

    private final UUID ID = UUID.randomUUID();
    
    private double[] polygonCoordinates;
    private double x;
    private double y;
    private double rotation;
    private float radius;
    private EntityCategory category = EntityCategory.UNKNOWN;
    private int maxHealth = 1;
    private int health = 1;


    public String getID() {
        return ID.toString();
    }


    public void setPolygonCoordinates(double... coordinates ) {
        this.polygonCoordinates = coordinates;
    }

    public double[] getPolygonCoordinates() {
        return polygonCoordinates;
    }
       

    public void setX(double x) {
        this.x =x;
    }

    public double getX() {
        return x;
    }

    
    public void setY(double y) {
        this.y = y;
    }

    public double getY() {
        return y;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public double getRotation() {
        return rotation;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }
        
    public float getRadius() {
        return this.radius;
    }

    public void setCategory(EntityCategory category) {
        this.category = category;
    }

    public EntityCategory getCategory() {
        return category;
    }

    /**
     * Sets the hit-point ceiling for this entity and immediately refills it
     * to full - meant to be called once, right after construction, by
     * whichever plugin decides how tough a given entity should be (defaults
     * to 1, i.e. destroyed on the first hit, for entities that never call
     * this).
     */
    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getHealth() {
        return health;
    }

    /**
     * Applies damage, floored at 0. Callers should check {@link #isDestroyed()}
     * afterwards to decide whether to remove the entity from the world.
     */
    public void damage(int amount) {
        health = Math.max(0, health - amount);
    }

    public boolean isDestroyed() {
        return health <= 0;
    }
}
