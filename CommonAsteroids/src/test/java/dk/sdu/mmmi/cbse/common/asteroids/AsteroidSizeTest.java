package dk.sdu.mmmi.cbse.common.asteroids;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AsteroidSizeTest {

    @Test
    void largeSplitsIntoMedium() {
        assertEquals(AsteroidSize.MEDIUM, AsteroidSize.LARGE.smaller());
    }

    @Test
    void mediumSplitsIntoSmall() {
        assertEquals(AsteroidSize.SMALL, AsteroidSize.MEDIUM.smaller());
    }

    @Test
    void smallHasNoSmallerSize() {
        assertNull(AsteroidSize.SMALL.smaller());
    }
}
