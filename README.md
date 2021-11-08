# Introduction
The typical suggested Spring-solution for wiring up your MyBatis mappers to multiple JDBC data sources is to aggregate the data sources into a `AbstractRoutingDatasource` object. An `AbstractRoutingDatasource` allows you to toggle which underlying `DataSource` it maps back to at runtime. This is a nice solution because it can plug in to existing infrastructure code that expects a `DataSource` object. The downside of using `AbstractRoutingDatasource` is that it does not integrate well into Spring Managed Transactions very well if you hope to be able to change the data source within a transaction. It seems that the underlying connection is retrieved once from the data source and is then managed by the transaction manager- so all future requests to the transaction manager will return the same connection regardless of whether the underlying data source has changed.

One direct solution to the problem is to manually manage transactions and do something like manually instantiate Mappers with `SqlSession` objects. You might also replicate all your mappers for each data source and add some additional infrastructure code to alternate between the mappers at runtime. These solutions are unwieldy in my opinion.

At first I was hoping to mess with the transaction manager and MyBatis infrastructure to see if I could get it to behave in a way that it would manage and return distinct resources for each underlying data source in the `AbstractRoutingDatasource`. I was not able to make this work. I do not doubt someone more clever and knowledgable when it comes to Spring and JEE might be able to make it work, but I personally do not think this avenue is worthwhile. You would have to contend with making sure Spring updates don't bork the workaround. I would imagine if you use an application server, you might have to validate the behavior of its transaction management infrastructure, which is likely somewhat opaque. Recompiling MyBatis or Spring projects would also not be ideal because it would again require regular maintenance and a deep knowledge of all the plumbing to make sure nothing get's broken.

# Potential Solution
Since bending Spring and MyBatis to my will to correctly use `AbstractRoutingDatasource` seems to be a royal pain, I decided to try pushing the routing farther down the chain. Instead of switching data sources, I configure two as follows:

```java
@Configuration
public class DataSources {

    @Bean
    @Qualifier("db1")
    public DataSource db2DataSource1(){
        var source = new AtomikosDataSourceBean();
        source.setXaDataSourceClassName("org.mariadb.jdbc.MariaDbDataSource");
        source.setUniqueResourceName("db1");
        source.setMaxPoolSize(5);

        Properties properties = new Properties();
        properties.setProperty("user", "user");
        properties.setProperty("password", "password");
        properties.setProperty("url", "jdbc:mariadb://interconnect1.internal.jbec.us:4306/db1");
        properties.setProperty("loginTimeout", "10");

        source.setXaProperties(properties);
        return source;
    }

    @Bean
    @Qualifier("db2")
    public DataSource db2DataSource2(){
        var source = new AtomikosDataSourceBean();
        source.setXaDataSourceClassName("org.mariadb.jdbc.MariaDbDataSource");
        source.setUniqueResourceName("db2");
        source.setMaxPoolSize(5);

        Properties properties = new Properties();
        properties.setProperty("user", "user");
        properties.setProperty("password", "password");
        properties.setProperty("url", "jdbc:mariadb://interconnect1.internal.jbec.us:4307/db2");
        properties.setProperty("loginTimeout", "10");

        source.setXaProperties(properties);
        return source;
    }

}

```

In addition, I configure two distinct `SqlSessionFactories` like this, each one using one of the configured data sources:

```java
@Configuration
public class SQLSessionFactories {

    @Primary
    @Bean
    @Qualifier("ssf1")
    public SqlSessionFactory sqlSessionFactory1(@Qualifier("db1") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        return sessionFactory.getObject();
    }

    @Bean
    @Qualifier("ssf2")
    public SqlSessionFactory sqlSessionFactory2(@Qualifier("db2") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        return sessionFactory.getObject();
    }
    
}
```

Now that we have two `SqlSessionFactory`, we need to somehow find a way to route them both behind a mapper object that can be injected into our beans. To start, I intentionally neglect to configure MyBatis to scan any of the mappers in the package my mapper interfaces live in (`us.jbec.experimental.routedmappers.mappers`).

Now, I manually handle getting those mapper beans into the application context. My idea is to manually create a MyBatis mapper for each data source, and use a JDK Proxy to route method invocations to the correct mapper. Much like a `AbstractRoutingDatasource`, I will use a `ThreadLocal` object key to determine which real mapper bean to invoke. To start, I define a configuration class with the required properties:

```java
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

    // ...
}

```

For my configuration of two data sources with all my mappers in `us.jbec.experimental.routedmappers.mappers`, I create this config bean:

```java
@Bean
public RoutedMapperBeanProperties properties(@Qualifier("ssf1") final SqlSessionFactory sqlSessionFactory1,
                                             @Qualifier("ssf2") final SqlSessionFactory sqlSessionFactory2) {

    Supplier<Object> supplier = RoutedMapperTargetContextHolder::getRoutedMapperTarget;

    Map<Object, SqlSessionFactory> resourceMap = new HashMap<>();
    resourceMap.put(RoutedMapperTarget.DB1, sqlSessionFactory1);
    resourceMap.put(RoutedMapperTarget.DB2, sqlSessionFactory2);

    String[] scanPaths = {"us.jbec.experimental.routedmappers.mappers"};

    return new RoutedMapperBeanProperties(supplier, resourceMap, scanPaths);
}

```

Now is the more interesting part. We need to load the Mapper interfaces, create proxies, and add them to the context. I used a configuration class `RoutedMapperBeanLoader` to do that work. It starts by using `ClassPathScanningCandidateComponentProvider` from Spring to find all the classes in the provided scan path(s). Currently I do not discriminate, all interfaces in this path are considered to be MyBatis mappers that need to be proxied.

```java
@Configuration
public class RoutedMapperBeanLoader {

    private final GenericApplicationContext context;
    private final RoutedMapperBeanProperties properties;
    private final List<Class<?>> classesToLoad = new ArrayList<>();


    public RoutedMapperBeanLoader(GenericApplicationContext context,
                                  RoutedMapperBeanProperties properties) throws Exception {
        this.context = context;
        this.properties = properties;

        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface();
            }
        };

        provider.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        for (String path : properties.getScanPaths()) {
            Set<BeanDefinition> beanDefinitions = provider.findCandidateComponents(path);
            for (BeanDefinition definition : beanDefinitions) {
                classesToLoad.add(Class.forName(definition.getBeanClassName()));
            }
        }

        addBeansToContext();
    }
    // ...
```

Now we need to configure each class and add it to Spring's application context. For each mapper interface, I configure a `MapperFactoryBean` for each `SqlSessionFactory` (which in turn is each wired to a different data source), and create a map from the available data source keys to the corresponding mapper factory. I then create a JDK proxy instance of the class with an attached `InvocationHandler` to do the routing (described next). The proxied instance is then registered with the context so that it can be auto-wired as needed.

```java
private void addBeansToContext() {
        for (Class<?> clazz : this.classesToLoad) {
            processClass(clazz);
        }
    }

    private <T> void processClass(Class<T> clazz) {
        ConcurrentHashMap<Object, MapperFactoryBean<T>> invocationTargetMap = new ConcurrentHashMap<>();

        for (Map.Entry<Object, SqlSessionFactory> entry : properties.getSqlSessionFactoriesConcurrentHashMap().entrySet()) {
            SqlSessionFactory sqlSessionFactory = entry.getValue();
            MapperFactoryBean<T> mapperFactoryBean = new MapperFactoryBean<>(clazz);
            sqlSessionFactory.getConfiguration().addMapper(clazz);
            mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
            invocationTargetMap.put(entry.getKey(), mapperFactoryBean);
        }

        Object proxyInstance = Proxy.newProxyInstance(clazz.getClassLoader(),
                new Class[]{clazz},
                new RoutedInvocationHandler<>(invocationTargetMap, clazz, properties.getInvocationTargetSupplier()));

        context.registerBean(clazz, () -> (T) proxyInstance);

    }
```

The `InvocationHandler` is pretty straight forward. It just delegates any method calls to the underlying mapper depending on the configured supplier.

```java
private static class RoutedInvocationHandler<T> implements InvocationHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(RoutedInvocationHandler.class);

        private final ConcurrentHashMap<String, Method> methods = new ConcurrentHashMap<>();

        private final ConcurrentHashMap<Object, MapperFactoryBean<T>> invocationTargets;

        private final Supplier<Object> keySupplier;

        public RoutedInvocationHandler(ConcurrentHashMap<Object, MapperFactoryBean<T>> invocationTargets, Class<T> classToInvoke, Supplier<Object> keySupplier) {
            this.invocationTargets = invocationTargets;
            this.keySupplier = keySupplier;

            for (Method method : classToInvoke.getDeclaredMethods()) {
                this.methods.put(method.getName(), method);
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            Object key = keySupplier.get();
            Assert.notNull(key, "Tried to invoke mapper without setting target!");
            LOGGER.debug("Invoked method: {} with target {}", method.getName(), key);
            MapperFactoryBean<T> mfb = invocationTargets.get(key);
            return methods.get(method.getName()).invoke(mfb.getObject(), args);
        }
    }    
```

# Conclusion
In my own limited testing, this seems to work fairly well for the examples I tried. I did not extensively test anything yet or create any test classes, just wired up a controller with some basic tests methods. I am somewhat optimistic because it does not hack away at much infrastructure code, so I would be more hopeful that any issues that arise would be fixable. I am also not deeply familiar with the internals of MyBatis or Spring so maybe there is some gotcha I have not found yet. I could not find much help when I was researching solutions for this problem online, so my main intent is that this will be helpful to someone else who might be running into similar issues, or provide inspiration for someone to develop a better solution. The sample driver code for this project is in this repository.