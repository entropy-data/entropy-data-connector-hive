package entropydata.hive;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "entropydata.client.hive")
public record HiveProperties(
        ConnectionProperties connection,
        AssetsProperties assets
) {

    public record ConnectionProperties(
            String host,
            int port,
            String database,
            String username,
            String password,
            String driverClassName,
            String jdbcUrl
    ) {
    }

    public record AssetsProperties(
            Boolean enabled,
            String connectorid,
            Duration pollinterval,
            DetailedTableInfoMode detailedTableInfo,
            String idPrefix,
            String owner
    ) {
    }
}