package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;
import dk.sdu.mmmi.cbse.common.services.IPostEntityProcessingService;
import dk.sdu.mmmi.cbse.common.util.ServiceLocator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring container's beans from whatever plugins
 * {@link ServiceLocator} finds in the {@code plugins/} module layer at
 * startup - Core's own module-info never {@code requires} Player, Enemy,
 * Bullet, Asteroids, or Collision directly; every one of them is discovered
 * dynamically here.
 *
 * @author jcs
 */
@Configuration
class ModuleConfig {

    public ModuleConfig() {
    }

    @Bean
    public Game game(){
        return new Game(gamePluginServices(), entityProcessingServiceList(), postEntityProcessingServices(), scoreClient());
    }

    @Bean
    public ScoreClient scoreClient() {
        return new ScoreClient();
    }

    @Bean
    public List<IEntityProcessingService> entityProcessingServiceList(){
        return ServiceLocator.INSTANCE.locateAll(IEntityProcessingService.class);
    }

    @Bean
    public List<IGamePluginService> gamePluginServices() {
        return ServiceLocator.INSTANCE.locateAll(IGamePluginService.class);
    }

    @Bean
    public List<IPostEntityProcessingService> postEntityProcessingServices() {
        return ServiceLocator.INSTANCE.locateAll(IPostEntityProcessingService.class);
    }
}
