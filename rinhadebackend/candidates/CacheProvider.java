package caravanacloud;

import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;

public class CacheProvider {
    // Set up a clustered Cache Manager.
    GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
    // Initialize the default Cache Manager.
    DefaultCacheManager cacheManager = new DefaultCacheManager(global.build());
    ConfigurationBuilder builder = new ConfigurationBuilder();


    @Produces
    @Default
    @DefaultBean
    public Cache<Integer, Cliente> newCache() {
        Cache<Integer, Cliente> cache = cacheManager.administration().withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("clientesCache", builder.build());
        return cache;

    }
    public void onStartup(@Observes StartupEvent ev) {
        // Create a distributed cache with synchronous replication.
        builder.clustering().cacheMode(CacheMode.DIST_SYNC);
    }

    public void onShutdown(@Observes ShutdownEvent ev) {
        // Shut down the cache manager and release all resources.
        cacheManager.stop();
    }

}
