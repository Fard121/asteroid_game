module Core {
    requires Common;

    requires CommonBullet;
    // Not used directly by Core - required so CommonAsteroids is part of
    // the boot layer's configuration, which the plugins/ ModuleLayer
    // (built by Common's ServiceLocator, parented on the boot
    // configuration) needs in order to resolve Collision/Asteroid's own
    // "requires CommonAsteroids".
    requires CommonAsteroids;
    requires javafx.graphics;
    requires spring.context;
    requires spring.core;
    requires spring.beans;
    requires spring.web;
    // Transitive runtime dependency of spring-web's RestTemplate in this
    // Spring version - must be resolvable on Core's own module path since
    // (unlike Player/Bullet/Enemy/Asteroids/Collision) Core isn't loaded
    // via ServiceLoader/automatic-module binding.
    requires micrometer.observation;
    // Lets RestTemplate serialize ScoreClient's request body to JSON -
    // transitively pulls in com.fasterxml.jackson.core and
    // com.fasterxml.jackson.annotation too.
    requires com.fasterxml.jackson.databind;
    exports dk.sdu.mmmi.cbse.main;
    opens dk.sdu.mmmi.cbse.main to javafx.graphics,spring.core,com.fasterxml.jackson.databind;
    // Plugin discovery now goes through Common's ServiceLocator, which
    // resolves Player/Enemy/Bullet/Asteroids/Collision into their own
    // ModuleLayer from the plugins/ folder - Core itself no longer calls
    // ServiceLoader.load for these directly, so it declares no `uses` here.
}


