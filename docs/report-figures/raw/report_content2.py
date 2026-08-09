"""Second half of the report: Implementation, Test, Discussion, Conclusion, References."""


def build(doc, H, P, rich, bullet, code, figure, table, pagebreak, cap,
          WD_ALIGN_PARAGRAPH, Pt, GREY):

    # ------------------------------------------------------------ implementation
    H(doc, "5  Implementation", 1)
    P(doc,
      "This section explains how the design is realised in source code: how components are registered "
      "and accessed, how reliable configuration and strong encapsulation are obtained, and where each "
      "component model appears in the repository.")

    H(doc, "5.1  Registration and access of components", 2)
    P(doc,
      "Components are never registered explicitly. A component joins the running game solely by "
      "declaring a provides clause in its module descriptor; nothing imports it and nothing lists it. "
      "The single point of assembly is ServiceLocator in the Common module, which builds the child "
      "module layer once and then answers lookups against it.")
    code(doc, """
Path pluginsDir = Paths.get("plugins");
ModuleFinder pluginsFinder = ModuleFinder.of(pluginsDir);

List<String> plugins = pluginsFinder.findAll().stream()
        .map(ModuleReference::descriptor)
        .map(ModuleDescriptor::name)
        .collect(Collectors.toList());

Configuration pluginsConfiguration = ModuleLayer
        .boot().configuration()
        .resolve(pluginsFinder, ModuleFinder.of(), plugins);

layer = ModuleLayer.boot()
        .defineModulesWithOneLoader(pluginsConfiguration, ClassLoader.getSystemClassLoader());
""", "ServiceLocator builds the child ModuleLayer from whatever jars are present in plugins/. "
     "Common/src/main/java/dk/sdu/mmmi/cbse/common/util/ServiceLocator.java")
    P(doc,
      "The set of components is therefore determined by the contents of a directory at start-up, not by "
      "anything written in the source. Figure 9 traces the full bootstrap, from the launch command to "
      "the first frame.")
    figure(doc, "fig09-seq-bootstrap.png",
           "Application start-up. Spring asks ServiceLocator for each service type, ServiceLocator "
           "resolves the plugins directory into a child ModuleLayer, and Game receives the discovered "
           "lists without ever naming a concrete component.", 15.5)

    H(doc, "5.2  Reliable configuration and strong encapsulation", 2)
    P(doc,
      "The module descriptors are where NFR2, NFR3 and NFR4 are enforced. Two properties are worth "
      "reading carefully in the excerpt below. Core requires only shared API modules, so the compiler "
      "physically cannot allow it to reach into a gameplay component. The Player component declares "
      "uses BulletSPI but never requires Bullet, so it compiles, and runs, whether or not a weapon "
      "implementation exists.")
    code(doc, """
module Core {                              module Player {
    requires Common;                           requires Common;
    requires CommonBullet;                     requires CommonBullet;
    requires CommonAsteroids;                  uses     dk.sdu.mmmi.cbse.common.bullet.BulletSPI;
    requires javafx.graphics;                  provides IGamePluginService
    requires spring.context;                       with ...playersystem.PlayerPlugin;
    exports  dk.sdu.mmmi.cbse.main;            provides IEntityProcessingService
    opens    dk.sdu.mmmi.cbse.main                 with ...playersystem.PlayerControlSystem;
        to javafx.graphics, spring.core;   }
    // no requires Player / Enemy /
    // Bullet / Asteroids / Collision     module Bullet {
}                                              requires Common;
                                               requires CommonBullet;
                                               provides BulletSPI
                                                   with ...bulletsystem.BulletControlSystem;
                                           }
""", "Module descriptors. Core names no gameplay module; Player consumes BulletSPI without depending "
     "on the component that provides it.")
    P(doc,
      "Encapsulation is asymmetric by design. The shared modules export their packages because their "
      "types are the contract. The five gameplay components export nothing at all: their classes are "
      "reachable only as service implementations, so no other component can bind to their internals "
      "even accidentally. This is a stricter reading of the exercise than exporting every package "
      "would be, and it is what makes substitution safe.")

    H(doc, "5.3  Component models applied", 2)
    P(doc,
      "Three distinct component models are used, each in the place where it fits. Their coexistence is "
      "deliberate: JPMS supplies reliable configuration, ServiceLoader supplies late binding, and Spring "
      "supplies runtime wiring.")
    table(doc,
          ["Component model", "Where it is applied", "Source location"],
          [
              ["Whiteboard model [15]",
               "A component publishes an implementation and is called by the framework; it never "
               "registers itself with, or looks up, the framework",
               "`Common/.../services/*.java` and every plugin module-info"],
              ["JPMS services",
               "provides ... with and uses declarations express the contract at module granularity, "
               "checked when the configuration resolves",
               "`*/src/main/java/module-info.java`"],
              ["ServiceLoader with ModuleLayer",
               "Late binding of implementations discovered from a directory rather than a class path",
               "`Common/.../util/ServiceLocator.java`"],
              ["Dependency injection",
               "The discovered lists are turned into beans and injected into the Game constructor",
               "`Core/.../main/ModuleConfig.java`"],
              ["Microservice [17], [18]",
               "The authoritative score is owned by a separate process behind a REST interface",
               "`Scoring/.../ScoreController.java`, `Core/.../ScoreClient.java`"],
          ],
          "Component models applied and where each appears in the repository.",
          widths=[3.8, 6.6, 4.9])
    code(doc, """
@Configuration
class ModuleConfig {

    @Bean public Game game() {
        return new Game(gamePluginServices(), entityProcessingServiceList(),
                        postEntityProcessingServices(), scoreClient());
    }

    @Bean public List<IEntityProcessingService> entityProcessingServiceList() {
        return ServiceLocator.INSTANCE.locateAll(IEntityProcessingService.class);
    }
    ...
}
""", "The Spring configuration wires whatever the module layer yielded. "
     "Core/src/main/java/dk/sdu/mmmi/cbse/main/ModuleConfig.java")

    H(doc, "5.4  Dynamic installation and uninstallation", 2)
    P(doc,
      "ComponentRegistry, held by Core, groups the already discovered service instances by the package "
      "of their implementing class and maintains the lists that the frame loop iterates. Installing or "
      "uninstalling a component is therefore a list operation plus a lifecycle call; no discovery is "
      "repeated, no class is reloaded, and nothing is recompiled.")
    code(doc, """
void toggle(String name, GameData gameData, World world) {
    for (Component component : components) {
        if (!component.name.equals(name)) continue;
        if (component.installed) {
            for (IGamePluginService plugin : component.plugins) plugin.stop(gameData, world);
            activePlugins.removeAll(component.plugins);
            activeProcessors.removeAll(component.processors);
        } else {
            for (IGamePluginService plugin : component.plugins) plugin.start(gameData, world);
            activePlugins.addAll(component.plugins);
            activeProcessors.addAll(component.processors);
        }
        component.installed = !component.installed;
        return;
    }
}
""", "Runtime installation and uninstallation. "
     "Core/src/main/java/dk/sdu/mmmi/cbse/main/ComponentRegistry.java")
    P(doc,
      "The weapon component illustrates why the contract must anticipate absence. BulletSPI.createBullet "
      "returns an Optional, and when the component is uninstalled it returns empty. Player and Enemy "
      "therefore keep running normally and simply stop producing bullets; neither contains any knowledge "
      "that the weapon can disappear beyond honouring the Optional. Figure 11 traces the sequence.")
    figure(doc, "fig13-seq-hotswap.png",
           "Runtime uninstallation and reinstallation of the weapon component. The Player component "
           "continues to run throughout, receiving an empty Optional while no weapon is installed.", 15.5)

    H(doc, "5.5  Collision, splitting and scoring", 2)
    P(doc,
      "CollisionDetector is the only implementer of IPostEntityProcessingService. It filters candidate "
      "pairs by category before performing any distance arithmetic, so friendly fire and "
      "asteroid-against-asteroid contacts are never considered, and it defers all removals until the "
      "scan is complete so that no pair is skipped or processed twice.")
    code(doc, """
public Boolean collides(Entity entity1, Entity entity2) {
    float dx = (float) entity1.getX() - (float) entity2.getX();
    float dy = (float) entity1.getY() - (float) entity2.getY();
    float distance = (float) Math.sqrt(dx * dx + dy * dy);
    return distance < (entity1.getRadius() + entity2.getRadius());
}
""", "Pythagorean collision test. "
     "Collision/src/main/java/dk/sdu/mmmi/cbse/collisionsystem/CollisionDetector.java")
    P(doc,
      "Three destruction paths are distinguished, and the difference between them is a design decision "
      "rather than an accident. A collision involving the player never removes the player entity; it "
      "registers a hit that the Player component consumes on the following frame, which is what allows "
      "lives and respawn invulnerability to live entirely inside that component. A ship that flies into "
      "an asteroid is destroyed outright, because a rock is not a bullet. Only a bullet kill awards "
      "points, so neither ramming nor a ship wrecked by an asteroid inflates the score.")
    figure(doc, "fig11-seq-collision.png",
           "Collision resolution. The splitter is reached through IAsteroidSplitter, so the Collision "
           "component never names a concrete asteroid type.", 15.0)
    P(doc,
      "Note in the figure that Collision obtains the splitter through a service lookup. It knows that "
      "something can split an asteroid; it does not know what, and if the Asteroids component were "
      "absent the lookup would simply yield nothing.")

    H(doc, "5.6  The scoring client", 2)
    P(doc,
      "ScoreClient is a thin wrapper over Spring's RestTemplate [6]. Making a plain JPMS module use "
      "RestTemplate required three additions to the module descriptor that are worth recording, because "
      "each was discovered by running the application rather than by reading documentation: "
      "spring.web itself; micrometer.observation, which RestTemplate references during class "
      "initialisation; and com.fasterxml.jackson.databind, without which no JSON message converter is "
      "registered and posting an object fails outright.")
    code(doc, """
class ScoreClient {
    private static final String SCORE_URL = "http://localhost:8081/api/score";
    private final RestTemplate restTemplate = new RestTemplate();

    void push(int score) {
        restTemplate.postForObject(SCORE_URL, new ScoreUpdateRequest(score), ScoreResponse.class);
    }
}
""", "Core/src/main/java/dk/sdu/mmmi/cbse/main/ScoreClient.java")
    P(doc,
      "The server side is a conventional Spring Boot REST controller [7] holding the score in an "
      "AtomicInteger, exposing GET and POST on /api/score and a reset endpoint. It carries no "
      "module-info: it is deployed on the class path as an ordinary executable jar, which reinforces "
      "that the boundary between the two processes is the HTTP contract and nothing else.")
    pagebreak(doc)

    # -------------------------------------------------------------------- test
    H(doc, "6  Test", 1)
    P(doc,
      "Validation was carried out at three levels: unit tests of individual components, an integration "
      "run of the complete deployment in its real configuration, and a controlled experiment "
      "demonstrating dynamic update. All evidence in this section is reproduced from actual runs of the "
      "built application.")

    H(doc, "6.1  Unit testing strategy", 2)
    P(doc,
      "The exercise asks for one unit test [12]. Twenty-one were written across five test classes, "
      "covering both examples the exercise names, moving the player ship and collision detection, and "
      "both testing styles its reading material describes. NFR5 is what makes this possible: because "
      "every component's logic is reachable through plain objects, no test requires a JavaFX stage.")
    table(doc,
          ["Test class", "Module", "Tests", "What it establishes"],
          [
              ["`CollisionDetectorTest`", "Collision", "6",
               "Pythagorean distance including the 3-4-5 case, multi-hit destruction, ship destroyed by "
               "an asteroid without scoring, and removal of both sides of a collision"],
              ["`PlayerStateTest`", "Common", "5",
               "Lives decrement to game over, hits ignored during respawn invulnerability, one-shot hit "
               "consumption, and reset"],
              ["`EntityTest`", "Common", "4",
               "Default single-hit destruction, survival until max health is depleted, damage floored at "
               "zero, and refill on setMaxHealth"],
              ["`PlayerControlSystemTest`", "Player", "3",
               "Rotation on arrow keys, thrust followed by friction, and wrapping at the screen edge"],
              ["`AsteroidSizeTest`", "CommonAsteroids", "3",
               "The split chain large to medium to small, and that the smallest size has no successor"],
          ],
          "Unit test inventory. 21 tests across 5 classes and 4 modules.",
          widths=[3.9, 2.5, 1.4, 7.5])
    P(doc,
      "The two styles are deliberately contrasted within one class. The state-based test builds a real "
      "World, fires three bullets one frame at a time, and asserts that the enemy survives the first two "
      "and is destroyed by the third. The interaction-based test replaces World with a Mockito mock [10] "
      "and verifies that removeEntity was called exactly twice, once per side of the collision, which is "
      "a claim about the interaction rather than about the resulting state.")
    code(doc, """
@Test
void enemyShipIsDestroyedByAnAsteroidWithoutScoringForThePlayer() {
    GameData gameData = new GameData();
    World world = new World();

    Entity enemy = entityAt(100, 100, 8, EntityCategory.ENEMY);
    enemy.setMaxHealth(3);                    // survives 3 bullets, but not one asteroid
    world.addEntity(enemy);
    world.addEntity(entityAt(100, 100, 14, EntityCategory.ASTEROID));

    detector.process(gameData, world);

    assertFalse(world.getEntities().contains(enemy), "enemy should be destroyed by the asteroid");
    assertEquals(0, gameData.getScoreState().getScore(), "a rock kill is not the player's kill");
}
""", "A state-based test of the contract in Section 5.5. "
     "Collision/src/test/java/dk/sdu/mmmi/cbse/collisionsystem/CollisionDetectorTest.java")

    H(doc, "6.2  Build and unit test results", 2)
    P(doc,
      "The full reactor builds and all tests pass. Figure 13 and Figure 14 are captured from the "
      "terminal session that produced the artefacts used everywhere else in this section.")
    figure(doc, "shot01-build.png",
           "mvn clean install over all eleven Maven modules, ending in BUILD SUCCESS and populating "
           "mods-mvn/ and plugins/.", 15.5)
    figure(doc, "shot02-tests.png",
           "mvn test. All 21 tests pass across the four modules that carry tests.", 13.0)

    H(doc, "6.3  Integration in a real deployment", 2)
    P(doc,
      "Integration was validated by running the system exactly as it is meant to be deployed: two "
      "processes, the game launched from the module path and the scoring service as a standalone jar. "
      "Figure 15 shows the scoring service starting on port 8081 and answering a full round trip: read "
      "the score, set it, read it back, reset it.")
    figure(doc, "shot03-microservice.png",
           "The scoring microservice starting and answering GET, POST and reset over HTTP.", 15.0)
    P(doc,
      "Figure 16 shows the application at its start menu, and Figure 17 during play with the components "
      "cooperating: the Player component's ship, the Enemy component's saucer, an asteroid from the "
      "Asteroids component, and bullets from the Bullet component, with the Collision component "
      "arbitrating between them. Every entity visible in that frame was contributed by a module that "
      "Core does not name.")
    figure(doc, "shot04-startmenu.png",
           "The application at start-up, in the START_MENU state, rendered by the Core component.", 10.5)
    figure(doc, "shot06-weapon-installed.png",
           "Gameplay with all five components installed. Player ship and enemy saucer both carry health "
           "bars; player bullets are yellow and enemy bullets orange.", 10.5)

    H(doc, "6.4  Experimental validation of dynamic update", 2)
    P(doc,
      "This is the experiment that addresses NFR1 and the deliverable required by the course "
      "information sheet. It was performed twice, at two different levels, because the two demonstrate "
      "different claims.")
    P(doc, "Experiment 1: uninstallation while the process runs.", bold=True, space_after=2)
    P(doc,
      "With the game running, the Weapon component was uninstalled by pressing key 3, the fire key was "
      "pressed repeatedly, and the component was then reinstalled and the fire key pressed again. "
      "Figure 18 shows the three states. In the first, bullets are in flight. In the second, no bullet "
      "exists anywhere on the field despite repeated firing, because BulletSPI.createBullet is returning "
      "an empty Optional. In the third, firing works again. The player ship continues to fly throughout, "
      "and no other component is disturbed.")
    figure(doc, "shot06-weapon-installed.png",
           "Weapon component installed: bullets in flight.", 10.0)
    figure(doc, "shot07-weapon-uninstalled.png",
           "Weapon component uninstalled at runtime with key 3. The fire key was pressed repeatedly and "
           "no bullet exists on the field; the rest of the game continues normally.", 10.0)
    figure(doc, "shot08-weapon-reinstalled.png",
           "Weapon component reinstalled with key 3. Firing resumes immediately, with no recompilation "
           "and no restart of the JVM.", 10.0)

    P(doc, "Experiment 2: removal from the deployment.", bold=True, space_after=2)
    P(doc,
      "The first experiment shows a component being switched off inside a running process, but the "
      "instances were still discovered at start-up. The stronger claim is that a component need not be "
      "present at all. To test this, the Enemy component's jar was moved out of the plugins directory "
      "and the application was launched again. No source file was touched and no module was rebuilt.")
    figure(doc, "shot10-jar-removal.png",
           "The Enemy component removed from the deployment by moving its jar out of plugins/. Core is "
           "not rebuilt.", 14.0)
    P(doc,
      "The application started normally and played correctly with four components instead of five. No "
      "enemy saucer ever appeared, no enemy bullets were fired, and nothing failed: the module layer "
      "simply resolved a smaller set of modules, and the lists Spring injected were shorter by one "
      "entry.")
    figure(doc, "shot09-enemy-jar-removed.png",
           "The game running with the Enemy component absent from the deployment. Player, Asteroids, "
           "Bullet and Collision continue to function; no saucer spawns.", 10.5)
    P(doc,
      "The jar was then moved back and the enemy returned on the next launch. Taken together the two "
      "experiments establish NFR1 at both levels the exercise cares about: a component can be disabled "
      "in a live process, and a component can be absent from the deployment entirely, in both cases "
      "without recompiling the source code.")

    H(doc, "6.5  Summary of results", 2)
    table(doc,
          ["Requirement", "Method of validation", "Result"],
          [
              ["FR1 to FR11", "Unit tests plus observation of the running game (Figures 16, 17)", "Met"],
              ["FR12", "HTTP round trip against the running service (Figure 15)", "Met"],
              ["NFR1", "Experiments 1 and 2 (Figures 18 to 22)", "Met at both runtime and deployment level"],
              ["NFR2", "Inspection of Core/module-info.java; no gameplay module is named", "Met"],
              ["NFR3", "Inspection of the five plugin descriptors; none exports a package", "Met"],
              ["NFR4", "Resolution occurs in the ServiceLocator constructor and fails there if a module is missing", "Met"],
              ["NFR5", "21 unit tests run with no JavaFX stage (Figure 14)", "Met"],
              ["NFR6", "Game observed running normally with the scoring service stopped", "Met"],
          ],
          "Validation summary.",
          widths=[2.8, 8.2, 4.3])
    pagebreak(doc)

    # -------------------------------------------------------------- discussion
    H(doc, "7  Discussion", 1)

    H(doc, "7.1  How well the design met the requirements", 2)
    P(doc,
      "The decisive requirement was NFR1, and the two experiments in Section 6.4 satisfy it directly. "
      "What is worth examining is why they succeed, because the outcome rests on a structural property "
      "rather than on a feature. Core cannot break when a component is removed because Core was never "
      "able to refer to that component in the first place. The compiler enforces this through the module "
      "descriptor, so the guarantee is not a matter of discipline that a later change might erode.")
    P(doc,
      "The same reasoning explains why the weapon experiment is the most informative of the three. "
      "Player and Enemy genuinely depend on a weapon to function as intended, and yet neither fails when "
      "it disappears, because the dependency is expressed as an Optional returned by a service rather "
      "than as a field of a known type. The contract anticipates absence, which is what distinguishes a "
      "component boundary from a mere package boundary.")

    H(doc, "7.2  Trade-offs accepted", 2)
    P(doc,
      "Indirection has a cost, and it would be dishonest to present the architecture as free. Three "
      "costs are visible in the code.")
    bullet(doc, "Traceability. Reading the source does not reveal which components will run; that is "
                "determined by a directory at start-up. The behaviour is discoverable only by running "
                "the application or listing plugins/, which is a real loss of static comprehensibility.")
    bullet(doc, "Service lookup cost. Player and Enemy resolve BulletSPI through the module layer on "
                "each shot rather than caching a provider. At sixty frames per second with a firing "
                "cooldown this is not measurable, but it is a deliberate simplicity-over-efficiency "
                "choice rather than an oversight.")
    bullet(doc, "Coordination through shared state. Collision cannot call into the Player component, so "
                "a hit is communicated by setting a flag in PlayerState that the Player component "
                "consumes on the next frame. This keeps the two components independent, but it moves "
                "part of the protocol into shared mutable state, where it is less explicit than a method "
                "call would be.")

    H(doc, "7.3  Limitations", 2)
    P(doc,
      "The most significant limitation is that ServiceLocator constructs its module layer exactly once. "
      "A jar added to plugins/ after start-up is not seen, so Experiment 2 requires a relaunch even "
      "though it requires no recompilation. Supporting genuine hot deployment would mean building a new "
      "layer and migrating live entities between loaders, which is a substantially harder problem and "
      "well beyond what the exercise asks for. The boundary is therefore deliberate, and it is stated "
      "here rather than left for a reader to discover.")
    P(doc,
      "A second limitation is that the toggling mechanism groups service instances by the package name "
      "of their implementing class. This is a pragmatic mapping local to Core and it does not create a "
      "compile-time dependency, but it does encode a naming convention that a new component would have "
      "to follow to become toggleable. A more principled solution would carry the component name in the "
      "service contract itself.")
    P(doc,
      "Finally, the scoring service holds its state in memory. This satisfies the exercise, which asks "
      "for a microservice rather than for durable storage, but it means the externalised score is "
      "authoritative only for the lifetime of that process.")

    H(doc, "7.4  Reflection on the development process", 2)
    P(doc,
      "Two defects found during development shaped the final design more than any diagram did. The "
      "first was the frozen-game symptom described in Section 4.6, which established that a call across "
      "a process boundary must be positioned so that its failure cannot abort work that has nothing to "
      "do with it. The second was found while auditing the implementation against the exercise text: "
      "enemy ships passed through asteroids untouched, because the collision table listed the player "
      "against asteroids but not the enemy. The exercise says ships, in the plural. The fix was small, "
      "but the lesson is that a requirement expressed in one word of prose can be missed entirely by an "
      "implementation that otherwise looks complete, which is precisely the argument for writing "
      "explicit contracts and tests against them.")
    pagebreak(doc)

    # -------------------------------------------------------------- conclusion
    H(doc, "8  Conclusion", 1)
    P(doc,
      "This project set out to build an Asteroids game whose gameplay subsystems are genuine "
      "components, and to demonstrate that one of them can be removed and restored without recompiling "
      "the source code. Both objectives were met.")
    P(doc,
      "The resulting system is assembled from five gameplay components that the composition root cannot "
      "name. Their contracts are declared in shared API modules as JPMS services with explicit pre- and "
      "post-conditions, they are discovered at runtime by ServiceLoader from a plugins directory, they "
      "are resolved into their own ModuleLayer so that their resolution is isolated from the boot "
      "layer, and they are injected into the game loop by the Spring container. Score keeping was moved "
      "out of the process altogether, behind an HTTP contract, with the failure of that service "
      "contained so that it cannot affect the frame rate.")
    P(doc,
      "Validation was carried out on three levels. Twenty-one unit tests exercise the component "
      "contracts in isolation, with no JavaFX stage required. The complete two-process deployment was "
      "run and a score round trip confirmed end to end. Most importantly, two experiments established "
      "dynamic update at the two levels that matter: a component was uninstalled and reinstalled inside "
      "a live process, and a component was removed from the deployment entirely, in both cases with no "
      "recompilation of any source file.")
    P(doc,
      "The wider result is the one the course is aimed at. Each exercise replaced a direct dependency "
      "with a discovered one, and the cumulative effect is a composition root whose module descriptor "
      "names no gameplay module at all. That absence is not a stylistic preference; it is the property "
      "that makes the parts of this system replaceable, and it is what the experiments in Section 6.4 "
      "ultimately measure.")

    # -------------------------------------------------------------- references
    H(doc, "9  References", 1)
    refs = [
        "S. Mak and P. Bakker, Java 9 Modularity: Patterns and Practices for Developing Maintainable "
        "Applications. Sebastopol, CA, USA: O'Reilly Media, 2017.",
        "M. Reinhold, \"JSR 376: Java Platform Module System,\" Java Community Process, Sep. 2017. "
        "[Online]. Available: https://openjdk.org/projects/jigsaw/spec/",
        "Oracle Corporation, \"Class ServiceLoader,\" Java Platform, Standard Edition API "
        "Specification, 2023. [Online]. Available: "
        "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html",
        "Oracle Corporation, \"Creating Extensible Applications,\" The Java Tutorials, 2023. [Online]. "
        "Available: https://docs.oracle.com/javase/tutorial/ext/basics/spi.html",
        "C. Szyperski, Component Software: Beyond Object-Oriented Programming, 2nd ed. Boston, MA, USA: "
        "Addison-Wesley, 2002.",
        "VMware, Inc., \"Spring Framework Reference Documentation, version 6.1,\" 2024. [Online]. "
        "Available: https://docs.spring.io/spring-framework/reference/",
        "VMware, Inc., \"Spring Boot Reference Documentation, version 3.2,\" 2024. [Online]. Available: "
        "https://docs.spring.io/spring-boot/docs/current/reference/html/",
        "OpenJFX Project, \"JavaFX 21 API Documentation,\" 2023. [Online]. Available: "
        "https://openjfx.io/javadoc/21/",
        "JUnit Team, \"JUnit 5 User Guide,\" 2024. [Online]. Available: "
        "https://junit.org/junit5/docs/current/user-guide/",
        "Mockito Contributors, \"Mockito Framework Documentation,\" 2024. [Online]. Available: "
        "https://site.mockito.org/",
        "R. C. Martin, \"The Dependency Inversion Principle,\" C++ Report, vol. 8, no. 6, pp. 61-66, "
        "1996.",
        "Course teaching staff, \"Component Based Systems: Laboratory Exercises (labs.pdf),\" "
        "Maersk Mc-Kinney Moller Institute, University of Southern Denmark, 2026.",
        "C. Larman, Applying UML and Patterns: An Introduction to Object-Oriented Analysis and Design "
        "and Iterative Development, 3rd ed. Upper Saddle River, NJ, USA: Prentice Hall, 2004.",
        "N. Llopis, \"Data-Oriented Design (or Why You Might Be Shooting Yourself in the Foot with "
        "OOP),\" Game Developer Magazine, pp. 11-13, Sep. 2009.",
        "OSGi Alliance, \"Listeners Considered Harmful: The Whiteboard Pattern,\" Technical Whitepaper "
        "revision 2.0, Aug. 2004.",
        "E. Gamma, R. Helm, R. Johnson, and J. Vlissides, Design Patterns: Elements of Reusable "
        "Object-Oriented Software. Reading, MA, USA: Addison-Wesley, 1994.",
        "J. Lewis and M. Fowler, \"Microservices: a definition of this new architectural term,\" "
        "martinfowler.com, Mar. 2014. [Online]. Available: "
        "https://martinfowler.com/articles/microservices.html",
        "R. T. Fielding, \"Architectural Styles and the Design of Network-based Software "
        "Architectures,\" Ph.D. dissertation, Univ. California, Irvine, CA, USA, 2000.",
    ]
    for i, ref in enumerate(refs, 1):
        p = P(doc, space_after=6)
        p.paragraph_format.left_indent = Pt(28)
        p.paragraph_format.first_line_indent = Pt(-28)
        r = p.add_run(f"[{i}]\t{ref}")
        r.font.size = Pt(11)
        r.font.name = "Times New Roman"
    pagebreak(doc)

    # ---------------------------------------------------------------- appendix
    H(doc, "Appendix A  Repository map", 1)
    table(doc,
          ["Path", "Contents"],
          [
              ["`Core/`", "Composition root: Main, Game, ModuleConfig, ComponentRegistry, HUDRenderer, ScoreClient"],
              ["`Common/`", "Entity, World, GameData and the state objects; the three service interfaces; ServiceLocator"],
              ["`CommonBullet/`", "Bullet entity and the BulletSPI contract"],
              ["`CommonAsteroids/`", "Asteroid entity, AsteroidSize and the IAsteroidSplitter contract"],
              ["`Player/`", "Player component: PlayerPlugin and PlayerControlSystem"],
              ["`Enemy/`", "Enemy component: EnemyPlugin and EnemyControlSystem"],
              ["`Bullet/`", "Weapon component: BulletPlugin and BulletControlSystem, provider of BulletSPI"],
              ["`Asteroids/`", "Asteroids component: plugin, processor and splitter implementation"],
              ["`Collision/`", "Collision component: CollisionDetector"],
              ["`Scoring/`", "Spring Boot scoring microservice, deployed as a standalone jar"],
              ["`mods-mvn/`", "Build output: boot layer module path"],
              ["`plugins/`", "Build output: component jars discovered at start-up"],
              ["`docs/`", "Architecture notes, microservice notes, the split-package demonstration, and this report"],
              ["`docs/report-figures/`", "Mermaid sources for every diagram and the captured screenshots used above"],
          ],
          "Repository layout.",
          widths=[4.0, 11.3])

    H(doc, "Appendix B  Reproducing the results", 1)
    P(doc,
      "Every figure and measurement in Section 6 can be reproduced from a clean checkout with the "
      "following commands, run from the repository root. A JDK of version 17 or newer is required.")
    code(doc, """
git clone https://github.com/Fard121/asteroid_game.git
cd asteroid_game

mvn clean install                              # builds 11 modules, runs 21 tests,
                                               # populates mods-mvn/ and plugins/

java -jar Scoring/target/Scoring-1.0.1-SNAPSHOT.jar    # terminal 2: scoring service, port 8081

mvn exec:exec --non-recursive                  # terminal 1: launches the game

curl http://localhost:8081/api/score           # terminal 3: score matches the in-game HUD
""", "Full reproduction sequence.")
    P(doc,
      "To reproduce Experiment 2 of Section 6.4, move plugins/Enemy-1.0.1-SNAPSHOT.jar out of the "
      "directory and launch the game again. No rebuild is required, and the enemy saucer will not "
      "appear. Moving the jar back restores it on the following launch.")
    P(doc,
      "The --non-recursive flag matters: it runs the exec plugin once for the root project only. "
      "Without it, Maven repeats the launch for every child module.")
