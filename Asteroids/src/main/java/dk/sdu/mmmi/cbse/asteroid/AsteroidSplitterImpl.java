package dk.sdu.mmmi.cbse.asteroid;

import dk.sdu.mmmi.cbse.common.asteroids.Asteroid;
import dk.sdu.mmmi.cbse.common.asteroids.AsteroidSize;
import dk.sdu.mmmi.cbse.common.asteroids.IAsteroidSplitter;
import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.World;

import java.util.Random;

/**
 *
 * @author corfixen
 */
public class AsteroidSplitterImpl implements IAsteroidSplitter {

    private final Random random = new Random();

    @Override
    public void createSplitAsteroid(Entity e, World world) {
        if (!(e instanceof Asteroid)) {
            return;
        }
        Asteroid destroyed = (Asteroid) e;
        AsteroidSize smallerSize = destroyed.getSize().smaller();
        if (smallerSize == null) {
            return; // SMALL asteroids just disappear
        }

        for (int i = 0; i < 2; i++) {
            world.addEntity(createAsteroidOfSize(smallerSize, destroyed.getX(), destroyed.getY()));
        }
    }

    private Entity createAsteroidOfSize(AsteroidSize size, double x, double y) {
        Asteroid asteroid = new Asteroid();
        asteroid.setSize(size);
        asteroid.setPolygonCoordinates(AsteroidShapes.jaggedPolygon(size.getRadius(), random));
        asteroid.setX(x);
        asteroid.setY(y);
        asteroid.setRadius(size.getRadius());
        asteroid.setRotation(random.nextInt(360));
        return asteroid;
    }

}
