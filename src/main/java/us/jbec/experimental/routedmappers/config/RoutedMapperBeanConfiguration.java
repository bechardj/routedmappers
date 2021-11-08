package us.jbec.experimental.routedmappers.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import us.jbec.experimental.routedmappers.models.RoutedMapperTarget;
import us.jbec.experimental.routedmappers.models.RoutedMapperTargetContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Configuration
public class RoutedMapperBeanConfiguration {

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

}
