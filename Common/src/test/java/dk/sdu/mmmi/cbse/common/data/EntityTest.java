package dk.sdu.mmmi.cbse.common.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTest {

    @Test
    void defaultsToOneHitDestruction() {
        Entity entity = new Entity();

        assertEquals(1, entity.getMaxHealth());
        assertEquals(1, entity.getHealth());
        assertFalse(entity.isDestroyed());

        entity.damage(1);

        assertEquals(0, entity.getHealth());
        assertTrue(entity.isDestroyed());
    }

    @Test
    void survivesUntilMaxHealthIsDepleted() {
        Entity entity = new Entity();
        entity.setMaxHealth(3);

        entity.damage(1);
        assertEquals(2, entity.getHealth());
        assertFalse(entity.isDestroyed());

        entity.damage(1);
        assertEquals(1, entity.getHealth());
        assertFalse(entity.isDestroyed());

        entity.damage(1);
        assertEquals(0, entity.getHealth());
        assertTrue(entity.isDestroyed());
    }

    @Test
    void healthNeverGoesNegative() {
        Entity entity = new Entity();
        entity.setMaxHealth(2);

        entity.damage(5);

        assertEquals(0, entity.getHealth());
        assertTrue(entity.isDestroyed());
    }

    @Test
    void setMaxHealthRefillsCurrentHealth() {
        Entity entity = new Entity();
        entity.setMaxHealth(3);
        entity.damage(2);

        entity.setMaxHealth(3);

        assertEquals(3, entity.getHealth());
        assertFalse(entity.isDestroyed());
    }
}
