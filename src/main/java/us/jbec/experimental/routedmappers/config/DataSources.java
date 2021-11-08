package us.jbec.experimental.routedmappers.config;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Properties;


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
