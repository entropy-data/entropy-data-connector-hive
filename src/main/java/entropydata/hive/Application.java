package entropydata.hive;

import entropydata.sdk.EntropyDataAssetsSynchronizer;
import entropydata.sdk.EntropyDataClient;
import entropydata.sdk.EntropyDataStateRepositoryRemote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "entropydata")
@ConfigurationPropertiesScan("entropydata")
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public EntropyDataClient entropyDataClient(
            @Value("${entropydata.client.host}") String host,
            @Value("${entropydata.client.apikey}") String apiKey) {
        return new EntropyDataClient(host, apiKey);
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(value = "entropydata.client.hive.assets.enabled", havingValue = "true")
    public EntropyDataAssetsSynchronizer entropyDataAssetsSynchronizer(
            HiveProperties hiveProperties,
            EntropyDataClient client,
            TaskExecutor taskExecutor) {
        try {
            var connectorId = hiveProperties.assets().connectorid();
            var stateRepository = new EntropyDataStateRepositoryRemote(connectorId, client);
            var assetsSupplier = new HiveAssetsSupplier(hiveProperties, stateRepository);
            var entropyDataAssetsSynchronizer = new EntropyDataAssetsSynchronizer(connectorId, client, assetsSupplier);
            if (hiveProperties.assets().pollinterval() != null) {
                entropyDataAssetsSynchronizer.setDelay(hiveProperties.assets().pollinterval());
            }

            taskExecutor.execute(entropyDataAssetsSynchronizer::start);
            return entropyDataAssetsSynchronizer;
        } catch (Exception e) {
            return new EntropyDataAssetsSynchronizer("test-connector", client, null);
        }
    }

    @Bean
    public SimpleAsyncTaskExecutor taskExecutor() {
        return new SimpleAsyncTaskExecutor();
    }
}