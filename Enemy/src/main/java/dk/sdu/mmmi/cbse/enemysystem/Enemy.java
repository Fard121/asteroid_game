package dk.sdu.mmmi.cbse.enemysystem;

import dk.sdu.mmmi.cbse.common.data.Entity;
import dk.sdu.mmmi.cbse.common.data.EntityCategory;

public class Enemy extends Entity {

    public Enemy() {
        setCategory(EntityCategory.ENEMY);
    }
}
