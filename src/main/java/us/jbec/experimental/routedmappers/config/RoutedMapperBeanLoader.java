package us.jbec.experimental.routedmappers.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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

}
