package dk.sdu.mmmi.cbse.common.asteroids;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;

/**
 *
 * @author corfixen
 */
public class Asteroid extends Entity {

    private AsteroidSize size = AsteroidSize.LARGE;

    public Asteroid() {
        setCategory(EntityCategory.ASTEROID);
    }

    public AsteroidSize getSize() {
        return size;
    }

    public void setSize(AsteroidSize size) {
        this.size = size;
    }
}
