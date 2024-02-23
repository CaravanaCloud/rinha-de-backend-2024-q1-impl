package caravanacloud.ispn;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;

import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.TreeSet;

public class CacheProducer {
    GlobalConfigurationBuilder global;
    DefaultCacheManager cacheManager;
    ConfigurationBuilder builder;

    @Produces
    public Cache<Integer, Cliente> newCache(){
        if (cacheManager == null)
            createCacheManager();
        Log.info("Creating cache");
        Cache<Integer, Cliente> cache = cacheManager
                    .administration()
                    .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                    .getOrCreateCache("rinhaCache", builder.build());
        return cache;
    }

    private synchronized void createCacheManager() {
        Log.info("Creating cache manager");
        global= GlobalConfigurationBuilder.defaultClusteredBuilder();
        global.serialization().marshaller(new org.infinispan.commons.marshall.JavaSerializationMarshaller());
        global.serialization().allowList()
                .addClasses(Cliente.class, Transacao.class, LinkedList.class, TreeSet.class, PriorityQueue.class, TransacaoComparator.class);

        cacheManager = new DefaultCacheManager(global.build());

        builder = new ConfigurationBuilder();
        builder.clustering().cacheMode(CacheMode.DIST_SYNC);
        builder
                .transaction()
                .transactionMode(TransactionMode.TRANSACTIONAL) // Enable transactional mode
                .lockingMode(LockingMode.PESSIMISTIC) // Set pessimistic locking mode
                .locking()
                .lockAcquisitionTimeout(60000L);
    }

    public void onStop(@Observes ShutdownEvent e){
        if (cacheManager != null){
            cacheManager.stop();
        }
    }


}
