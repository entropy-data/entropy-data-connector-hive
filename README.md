Entropy Data Connector for Hive
===

The connector for Hive-compatible systems is a Spring Boot application that uses the [entropy-data-sdk](https://github.com/entropy-data/entropy-data-sdk) internally, and is available as a ready-to-use Docker image [entropydata/entropy-data-connector-hive](https://hub.docker.com/r/entropydata/entropy-data-connector-hive) to be deployed in your environment. This connector allows Entropy Data to synchronize assets from Hive-compatible systems via JDBC. It supports Apache Hive, Apache Impala, and other Hive-compatible systems.

## Features

- **Asset Synchronization**: Sync tables and schemas from Hive-compatible systems to the Entropy Data as Assets via JDBC
- **Database Discovery**: Discovers databases, tables, and columns from Apache Hive, Apache Impala, and other Hive-compatible systems
- **Hierarchical Relationships**: Creates hierarchical asset relationships (database → table)
- **Detailed Table Information**: Supports parsing and storing detailed table metadata
- **Flexible Configuration**: Configurable asset ID prefixes, ownership, and connection parameters

## Usage

Start the connector using Docker. You must pass the API keys and connection details as environment variables.

```
docker run \
  -e ENTROPYDATA_CLIENT_APIKEY='insert-api-key-here' \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_HOST='your-hive-host' \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_PORT=10000 \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_USERNAME='your-username' \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_PASSWORD='your-password' \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_JDBC_URL='jdbc:hive2://your-hive-host:10000/default' \
  entropydata/entropy-data-connector-hive:latest
```

## Versions

Every release is published as an immutable image tag. Pin a version rather than following `latest`:

```
entropydata/entropy-data-connector-hive:0.3.0
```

| Tag | Meaning |
|---|---|
| `X.Y.Z` | A released version. Immutable, and the recommended way to run the connector. |
| `latest` | The most recent release. Moves with every release. |
| `sha-<commit>` | A single commit on `main`, published so that a change can be tried out before it is released. |

Release images are signed with [cosign](https://docs.sigstore.dev/), and carry an SBOM and build provenance.
Verifying needs **cosign 3 or later**, because the signatures use the OCI referrers format that cosign 2 cannot read
(it reports `no signatures found`):

```
cosign verify entropydata/entropy-data-connector-hive:0.3.0 \
  --certificate-identity-regexp 'https://github.com/entropy-data/entropy-data-connector-hive/.*' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

## Configuration

| Environment Variable | Default Value | Description |
|----------------------|---------------|-------------|
| `ENTROPYDATA_CLIENT_HOST` | `https://api.entropy-data.com` | Base URL of the Entropy Data API. |
| `ENTROPYDATA_CLIENT_APIKEY` | | API key for authenticating requests to the Entropy Data. |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_HOST` | `localhost` | Hive server hostname. |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_PORT` | `10000` | Hive server port. |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_DATABASE` | `default` | Default database. |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_USERNAME` | | Username for authentication. |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_PASSWORD` | | Password for authentication. |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_DRIVER_CLASS_NAME` | `org.apache.hive.jdbc.HiveDriver` | JDBC driver class name. |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_JDBC_URL` | `jdbc:hive2://localhost:10000/default` | Full JDBC connection URL. |
| `ENTROPYDATA_CLIENT_HIVE_ASSETS_CONNECTORID` | `hive-assets` | Identifier for the Hive assets connector. |
| `ENTROPYDATA_CLIENT_HIVE_ASSETS_ENABLED` | `true` | Indicates whether Hive asset tracking is enabled. |
| `ENTROPYDATA_CLIENT_HIVE_ASSETS_POLLINTERVAL` | `PT10M` | Polling interval for Hive asset updates, in ISO 8601 duration format. |
| `ENTROPYDATA_CLIENT_HIVE_ASSETS_DETAILED_TABLE_INFO` | `json` | How to handle detailed table information: `json`, `raw`, or `ignore`. |
| `ENTROPYDATA_CLIENT_HIVE_ASSETS_ID_PREFIX` | `hive-` | Prefix for all asset IDs. |
| `ENTROPYDATA_CLIENT_HIVE_ASSETS_OWNER` | | Default owner team ID for all assets. |


## Resources

The connector needs **at least 1 GB of container memory**. The image sets a heap limit accordingly:

```
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError
```

Without `MaxRAMPercentage`, the JVM caps the heap at 25% of the container memory. `ExitOnOutOfMemoryError` terminates the
container instead of leaving it running with a dead synchronization thread, so that your orchestrator can restart it.

Setting `JAVA_TOOL_OPTIONS` at runtime **replaces** these flags rather than adding to them. Repeat the flags you want to keep:

```
-e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -javaagent:/agent.jar'
```

Expect the container to use around 60% of its memory limit under load. Adjust memory alarms accordingly.

### Synchronization Health

The health endpoint reports whether the asset synchronization is still up to date:

```
curl http://localhost:8080/actuator/health
```

The `assetsSynchronizationHealth` component reports `DEGRADED` when the last run failed, or when no run has succeeded for three
poll intervals, and names the failure in `lastFailure`. It is deliberately not reported as `DOWN`, and the endpoint still responds
with 200, because the usual cause is an unavailable data platform, which restarting the container does not fix. Point liveness
probes at `/actuator/health/liveness`, which is unaffected by the synchronization state.

## Supported Systems

- Apache Hive
- Apache Impala
- Cloudera Hive/Impala (via compatible JDBC drivers)
- Any Hive-compatible system supporting standard SQL commands

## Requirements

- Java 17+
- Maven 3.6+
- Access to a Hive-compatible system
- Entropy Data API access

## JDBC Driver Examples

The connector supports various Hive-compatible JDBC drivers:

### Apache Hive
```properties
entropydata.client.hive.connection.driver-class-name=org.apache.hive.jdbc.HiveDriver
entropydata.client.hive.connection.jdbc-url=jdbc:hive2://localhost:10000/default
```

### Apache Impala
**Note**: Requires the Impala JDBC driver JAR file to be present in the `/drivers` folder. Maven will automatically include all JARs from this folder in the build.
```properties
entropydata.client.hive.connection.driver-class-name=org.apache.impala.jdbc.jdbc41.Driver
entropydata.client.hive.connection.jdbc-url=jdbc:impala://localhost:21000/default
```

### Cloudera Hive
**Note**: Requires the Cloudera Hive JDBC driver JAR file to be present in the `/drivers` folder. Maven will automatically include all JARs from this folder in the build.
```properties
entropydata.client.hive.connection.driver-class-name=com.cloudera.hive.jdbc.HS2Driver
entropydata.client.hive.connection.jdbc-url=jdbc:hive2://localhost:10000/default
```

### Cloudera Impala
**Note**: Requires the Cloudera Impala JDBC driver JAR file to be present in the `/drivers` folder. Maven will automatically include all JARs from this folder in the build.
```properties
entropydata.client.hive.connection.driver-class-name=com.cloudera.impala.jdbc.Driver
entropydata.client.hive.connection.jdbc-url=jdbc:impala://localhost:21050/default
```

## JDBC Driver Setup

The connector is configured to automatically include **all** JDBC driver JAR files placed in the `/drivers` folder during the Maven build process. This allows you to add any JDBC drivers without modifying the build configuration.

Simply place your JDBC driver JAR files in the `/drivers` folder:

```
/drivers/
├── HiveJDBC42.jar       # Example: Cloudera Hive driver
├── ImpalaJDBC42.jar     # Example: Cloudera Impala driver  
├── custom-driver.jar    # Any other JDBC driver
└── ...                  # Maven will include all *.jar files
```

During the build process:
1. Maven automatically copies all `.jar` files from `/drivers` to the classpath
2. The Spring Boot application can dynamically load these drivers at runtime
3. No code changes are required to add new drivers

## Detailed Table Information

The connector can extract detailed table information using `DESCRIBE EXTENDED` command:

- **`json`**: Parse the detailed information using HiveObjectParser and store as JSON object
- **`raw`**: Store the detailed information as raw string data  
- **`ignore`**: Skip processing detailed table information completely

## Asset Hierarchy

The connector creates a two-level hierarchy with parent relationships:

- **Database**: ID format: `{id-prefix}.{database}`
- **Table**: ID format: `{id-prefix}.{database}.{table}` (parent: database)

In Hive, databases and schemas are equivalent concepts, so the connector skips the schema level and creates tables directly under databases.

## Development and Testing

For development and testing purposes, the project includes convenient scripts to set up a local Hive environment using Docker:

### Setting up Local Hive Environment

#### start-hive.sh
Starts a local Apache Hive 4.1.0 container with HiveServer2 for development and testing:

```bash
./start-hive.sh
```

This script:
- Runs Apache Hive 4.1.0 in a Docker container named `hive4`
- Exposes HiveServer2 on port 10000 (JDBC connections)
- Exposes Hive Web UI on port 10002
- Provides access to the Web UI at http://localhost:10002/

#### start-beeline.sh
Connects to the local Hive instance using Beeline shell for interactive testing:

```bash
./start-beeline.sh
```

This script:
- Connects to the HiveServer2 instance running in the `hive4` container
- Provides example commands for testing:
  - `show databases;` - List available databases
  - `show tables;` - List tables in current database
  - `create table hive_example(a string, b int) partitioned by(c int);` - Create a test table

### Development Workflow

1. Start the local Hive environment:
   ```bash
   ./start-hive.sh
   ```

2. Configure the connector to use the local Hive instance in `application-local.properties`:
   ```properties
   entropydata.client.hive.connection.host=localhost
   entropydata.client.hive.connection.port=10000
   entropydata.client.hive.connection.jdbc-url=jdbc:hive2://localhost:10000/default
   ```

3. Test the connection using Beeline:
   ```bash
   ./start-beeline.sh
   ```

4. Run the connector with the local profile:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

## Building and Running

### Build the application
```bash
mvn clean package
```

### Run with Maven
```bash
mvn spring-boot:run
```

### Run the JAR directly
```bash
java -jar target/entropy-data-connector-hive-0.0.1-SNAPSHOT.jar
```

### Docker

#### Building the Docker Image
```bash
# Build the application first
mvn clean package

# Build the Docker image
docker build -t entropy-data-connector-hive .
```

#### Running with Docker
```bash
# Run with external configuration file
docker run -p 8080:8080 \
  -v $(pwd)/application.properties:/app/application.properties \
  entropy-data-connector-hive

# Run with environment variables
docker run -p 8080:8080 \
  -e ENTROPYDATA_CLIENT_HOST=https://api.entropy-data.com \
  -e ENTROPYDATA_CLIENT_APIKEY=your-api-key \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_HOST=your-hive-host \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_PORT=10000 \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_USERNAME=your-username \
  -e ENTROPYDATA_CLIENT_HIVE_CONNECTION_PASSWORD=your-password \
  entropy-data-connector-hive

# Run with custom JDBC drivers
docker run -p 8080:8080 \
  -v $(pwd)/drivers:/app/drivers \
  -v $(pwd)/application.properties:/app/application.properties \
  entropy-data-connector-hive
```

#### Docker Image Details
- **Base Image**: `openjdk:17-jre-slim`
- **Exposed Port**: 8080
- **Working Directory**: `/app`
- **Configuration**: Mount `application.properties` to `/app/application.properties`
- **JDBC Drivers**: Mount custom drivers to `/app/drivers` (automatically loaded)

#### Docker Environment Variables
Spring Boot automatically converts environment variables to configuration properties:

| Environment Variable | Configuration Property |
|---------------------|----------------------|
| `ENTROPYDATA_CLIENT_HOST` | `entropydata.client.host` |
| `ENTROPYDATA_CLIENT_APIKEY` | `entropydata.client.apikey` |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_HOST` | `entropydata.client.hive.connection.host` |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_PORT` | `entropydata.client.hive.connection.port` |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_USERNAME` | `entropydata.client.hive.connection.username` |
| `ENTROPYDATA_CLIENT_HIVE_CONNECTION_PASSWORD` | `entropydata.client.hive.connection.password` |

## Authentication and Security

The connector supports various authentication mechanisms depending on your Hive setup:

- **No Authentication**: Connect without credentials
- **Username/Password**: Basic authentication with username and password
- **Kerberos**: For secure environments (driver dependent)

Configure SSL connections through JDBC URL parameters as supported by your specific JDBC driver.

## Monitoring

The application exposes actuator endpoints for monitoring:

- Health check: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`

## Troubleshooting

### Common Issues

1. **JDBC Driver Not Found**: Ensure the correct JDBC driver is included in the classpath. The default setup includes Apache Hive JDBC driver.

2. **Connection Timeout**: Check network connectivity and firewall settings between the connector and Hive server.

3. **Authentication Failures**: Verify username, password, and authentication mechanism configuration.

4. **No Tables Found**: Ensure the user has permissions to access databases and tables in the Hive system.

### Logging

Enable debug logging for more detailed information:

```properties
logging.level.entropydata.hive=DEBUG
```

## License

MIT
