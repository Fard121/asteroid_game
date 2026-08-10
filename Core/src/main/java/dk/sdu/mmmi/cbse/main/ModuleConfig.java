package dk.sdu.mmmi.cbse.main;

import dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
import dk.sdu.mmmi.cbse.common.services.IGamePluginService;
import dk.sdu.mmmi.cbse.common.services.IPostEntityProcessingService;
import dk.sdu.mmmi.cbse.common.util.ServiceLocator;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

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

    // The three service lists are prototype-scoped so the container does
    // not hold a singleton List pinning every plugin's service instances
    // for the lifetime of the JVM. Those retained references would survive
    // a runtime `plugin unload` and keep the unloaded plugin's class loader
    // alive, so the plugin could never actually leave. Handing Game a fresh
    // list instead leaves ComponentRegistry as the single owner of the live
    // instances, which is what makes unloading effective. Discovery itself
    // is unchanged - the same ServiceLocator lookups, at the same point in
    // startup.

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public List<IEntityProcessingService> entityProcessingServiceList(){
        return ServiceLocator.INSTANCE.locateAll(IEntityProcessingService.class);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public List<IGamePluginService> gamePluginServices() {
        return ServiceLocator.INSTANCE.locateAll(IGamePluginService.class);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public List<IPostEntityProcessingService> postEntityProcessingServices() {
        return ServiceLocator.INSTANCE.locateAll(IPostEntityProcessingService.class);
    }
}
