package us.jbec.experimental.routedmappers.config;

import org.apache.ibatis.session.SqlSessionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Properties for RoutedMapperBeanLoader
 */
public class RoutedMapperBeanProperties {

    /**
     * Supplier providing a non-null object indicating which underlying mapper should be invoked
     */
    private final Supplier<Object> invocationTargetSupplier;

    /**
     * Mapping of object keys (provided by the invocationTargetSupplier) to SqlSessionFactory that should be injected into the mapper
     */
    private final ConcurrentHashMap<Object, SqlSessionFactory> sqlSessionFactoriesConcurrentHashMap;

    /**
     * Paths containing the interfaces the loader should build beans for
     */
    private final String[] scanPaths;

    public RoutedMapperBeanProperties(Supplier<Object> invocationTargetSupplier, Map<Object, SqlSessionFactory> sqlSessionFactoriesConcurrentHashMap, String[] scanPaths) {
        this.invocationTargetSupplier = invocationTargetSupplier;
        this.sqlSessionFactoriesConcurrentHashMap = new ConcurrentHashMap<>(sqlSessionFactoriesConcurrentHashMap);
        this.scanPaths = scanPaths;
    }

    public Supplier<Object> getInvocationTargetSupplier() {
        return invocationTargetSupplier;
    }

    public ConcurrentHashMap<Object, SqlSessionFactory> getSqlSessionFactoriesConcurrentHashMap() {
        return sqlSessionFactoriesConcurrentHashMap;
    }

    public String[] getScanPaths() {
        return scanPaths;
    }
}
