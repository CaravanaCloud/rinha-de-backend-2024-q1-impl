package caravanacloud;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;

public class CacheProducer {
    // Set up a clustered Cache Manager.    
    static GlobalConfigurationBuilder global;
    // Initialize the default Cache Manager.
    static DefaultCacheManager cacheManager;
    // Create a distributed cache with synchronous replication.
    static ConfigurationBuilder builder;
    static Cache<Integer, Cliente> cache;
    
    static {
        global = GlobalConfigurationBuilder
            .defaultClusteredBuilder();
        global.serialization()
            .allowList()
            .addClass(Transacao.class.getName())
            .addClass(Cliente.class.getName())
            .addClass(LinkedList.class.getName());
        cacheManager = new DefaultCacheManager(global.build());
        builder = new ConfigurationBuilder();
        builder.clustering()
            .cacheMode(CacheMode.DIST_SYNC)
            .encoding()
            .mediaType("application/x-java-serialized-object");
    
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("Shutting down cache...");
            cacheManager.stop();
        }));
        cache = cacheManager.administration()
            .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
            .getOrCreateCache("clientesCache", builder.build());
    }
    @Produces
    public Cache<Integer, Cliente> getCache(){
        return cache;
    }

    public void initCache(@Observes StartupEvent event){
        Log.info("Warming up cache...");
    }
}
