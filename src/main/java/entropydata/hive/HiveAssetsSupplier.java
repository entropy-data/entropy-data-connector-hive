package entropydata.hive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entropydata.sdk.EntropyDataAssetsProvider;
import entropydata.sdk.EntropyDataStateRepository;
import entropydata.sdk.client.model.Asset;
import entropydata.sdk.client.model.AssetColumnsInner;
import entropydata.sdk.client.model.AssetInfo;
import entropydata.sdk.client.model.AssetRelationshipsInner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;

/**
 * Supplies assets from Hive-compatible systems (Hive, Impala).
 *
 * This connector works with any Hive-compatible system that supports:
 * - SHOW DATABASES and SHOW TABLES commands
 * - DESCRIBE EXTENDED table_name syntax
 * - Standard JDBC connectivity
 *
 * Supported systems include:
 * - Apache Hive
 * - Apache Impala
 * - Apache Hive Server2
 * - Apache Impala
 *
 * Note on SQL Injection Protection:
 * - Database and table names are identifiers and cannot be parameterized in prepared statements
 * - They are sanitized and quoted using quoteIdentifier() method
 * - Any actual parameter values (like LIMIT numbers) use proper prepared statement parameters
 */
public class HiveAssetsSupplier implements EntropyDataAssetsProvider {

  private static final Logger log = LoggerFactory.getLogger(HiveAssetsSupplier.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final HiveProperties properties;
  private final EntropyDataStateRepository stateRepository;

  public HiveAssetsSupplier(HiveProperties properties,
      EntropyDataStateRepository stateRepository) {
    this.properties = properties;
    this.stateRepository = stateRepository;
  }

  @Override
  public void fetchAssets(AssetCallback callback) {
    Long lastUpdatedAt = getLastUpdatedAt();
    Long currentTimestamp = System.currentTimeMillis();

    try {
      // Load the JDBC driver class from classpath
      Class.forName(properties.connection().driverClassName());
      log.debug("JDBC driver loaded from classpath: {}", properties.connection().driverClassName());
    } catch (ClassNotFoundException e) {
      log.error("JDBC driver not found: {}. Make sure the driver is included in the classpath using Maven profiles.",
          properties.connection().driverClassName(), e);
      return;
    }

    try (Connection connection = DriverManager.getConnection(
        properties.connection().jdbcUrl(),
        properties.connection().username(),
        properties.connection().password())) {

      log.info("Synchronizing Hive assets from {}", getHost());

      extractDatabases(connection, callback);

      setLastUpdatedAt(currentTimestamp);

    } catch (SQLException e) {
      log.error("Error fetching assets from Hive", e);
    }
  }


  private void extractDatabases(Connection connection, AssetCallback callback) throws SQLException {
    try (PreparedStatement stmt = connection.prepareStatement("SHOW DATABASES");
        ResultSet rs = stmt.executeQuery()) {

      while (rs.next()) {
        String databaseName = rs.getString(1);

        if (shouldSkipDatabase(databaseName)) {
          continue;
        }

        log.info("Starting to process database: {}", databaseName);
        extractDatabaseAsset(databaseName, callback);
        extractTablesFromDatabase(connection, databaseName, callback);
        log.info("Completed processing database: {}", databaseName);
      }
    }
  }

  private boolean shouldSkipDatabase(String databaseName) {
    return databaseName.equals("information_schema") ||
        databaseName.equals("sys");
  }

  private void extractDatabaseAsset(String databaseName, AssetCallback callback) {
    log.info("Starting to extract database asset: {}", databaseName);

    Asset databaseAsset = new Asset();

    String assetId = getDatabaseAssetId(databaseName);
    databaseAsset.setId(assetId);

    AssetInfo info = new AssetInfo();
    info.setName(databaseName);
    info.setQualifiedName(databaseName);
    info.setType("hive_database");
    info.setStatus("active");
    info.setDescription("Hive database: " + databaseName);
    info.setSource("hive");
    info.setSourceId(databaseName);
    databaseAsset.setInfo(info);

    databaseAsset.putPropertiesItem("host", getHost());
    databaseAsset.putPropertiesItem("port", getPort());
    databaseAsset.putPropertiesItem("updatedAt", String.valueOf(System.currentTimeMillis()));

    String owner = getDefaultOwner();
    if (owner != null) {
      databaseAsset.putPropertiesItem("owner", owner);
    }

    log.info("Database asset JSON: {}", toJson(databaseAsset));
    callback.onAssetUpdated(databaseAsset);

    log.info("Completed extracting database asset: {}", databaseName);
  }

  private void extractTablesFromDatabase(Connection connection, String databaseName,
      AssetCallback callback) throws SQLException {

    try (PreparedStatement useStmt = connection.prepareStatement("USE " + quoteIdentifier(databaseName))) {
      useStmt.execute();
    }

    try (PreparedStatement stmt = connection.prepareStatement("SHOW TABLES");
        ResultSet rs = stmt.executeQuery()) {

      while (rs.next()) {
        String tableName = rs.getString(1);
        log.info("Starting to process table: {}.{}", databaseName, tableName);
        extractTableAsset(connection, databaseName, tableName, callback);
        log.info("Completed processing table: {}.{}", databaseName, tableName);
      }
    }
  }

  private void extractTableAsset(Connection connection, String databaseName, String tableName,
      AssetCallback callback) throws SQLException {
    log.info("Starting to extract table asset: {}.{}", databaseName, tableName);

    Asset tableAsset = new Asset();

    String assetId = getTableAssetId(databaseName, tableName);
    tableAsset.setId(assetId);

    AssetInfo info = new AssetInfo();
    info.setName(tableName);
    info.setQualifiedName(databaseName + "." + tableName);
    info.setType("hive_table");
    info.setStatus("active");
    info.setDescription("Hive table: " + tableName);
    info.setSource("hive");
    info.setSourceId(databaseName + "." + tableName);
    tableAsset.setInfo(info);

    // Set parent relationship - table belongs to schema
    // Note: Using properties until AssetRelationship is available in SDK
    String parentAssetId = getDatabaseAssetId(databaseName);
    tableAsset.addRelationshipsItem(new AssetRelationshipsInner().relationshipType("parent").assetId(parentAssetId));

    extractColumnsFromTable(connection, databaseName, tableName, tableAsset);

    tableAsset.putPropertiesItem("database", databaseName);

    tableAsset.putPropertiesItem("host", getHost());
    tableAsset.putPropertiesItem("port", getPort());

    tableAsset.putPropertiesItem("updatedAt", String.valueOf(System.currentTimeMillis()));

    String owner = getDefaultOwner();
    if (owner != null) {
      tableAsset.putPropertiesItem("owner", owner);
    }

    log.info("Table asset JSON: {}", toJson(tableAsset));
    callback.onAssetUpdated(tableAsset);

    log.info("Completed extracting table asset: {}.{}", databaseName, tableName);
  }

  private String getPort() {
    return String.valueOf(properties.connection().port());
  }

  private String getHost() {
    return properties.connection().host();
  }

  private void extractColumnsFromTable(Connection connection, String databaseName, String tableName,
      Asset tableAsset) throws SQLException {
    // Note: DESCRIBE statement requires table name as identifier, not parameter
    String quotedDatabaseName = quoteIdentifier(databaseName);
    String quotedTableName = quoteIdentifier(tableName);
    String describeQuery = "DESCRIBE EXTENDED " + quotedDatabaseName + "." + quotedTableName;

    try (PreparedStatement stmt = connection.prepareStatement(describeQuery);
        ResultSet rs = stmt.executeQuery()) {

      boolean foundDetailedInfo = false;
      boolean inDetailedSection = false;
      while (rs.next()) {
        String columnName = rs.getString("col_name");
        String columnType = rs.getString("data_type");
        String comment = rs.getString("comment");

        // If col_name is null or empty, switch to detailed info mode
        if (columnName == null || columnName.trim().isEmpty()) {
          inDetailedSection = true;
          continue;
        }

        // If we're in the detailed section, only look for Detailed Table Information
        if (inDetailedSection) {
          System.out.println("columnName = " + columnName);
          if ("Detailed Table Information".equals(columnName)) {
            foundDetailedInfo = true;
            parseDetailedTableInformation(columnType, tableAsset);
          }
          continue;
        }

        // Process regular columns only if not in detailed section
        AssetColumnsInner column = new AssetColumnsInner();
        column.setName(columnName);
        column.setType(columnType);
        column.setDescription(comment != null && !comment.isEmpty() ? comment : "");
        tableAsset.addColumnsItem(column);
      }

      if (!foundDetailedInfo) {
        log.debug("No detailed table information found for {}.{}", databaseName, tableName);
      }

    } catch (SQLException e) {
      log.warn("Could not describe table {}.{}: {}", databaseName, tableName, e.getMessage());

      // Note: Table name must be an identifier, but LIMIT value can be parameterized
      String fallbackQuery = "SELECT * FROM " + quotedDatabaseName + "." + quotedTableName + " LIMIT ?";
      try (PreparedStatement stmt = connection.prepareStatement(fallbackQuery)) {
        stmt.setInt(1, 0); // Set LIMIT parameter
        ResultSetMetaData metaData = stmt.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
          String columnName = metaData.getColumnName(i);
          String columnType = metaData.getColumnTypeName(i);

          AssetColumnsInner column = new AssetColumnsInner();
          column.setName(columnName);
          column.setType(columnType);
          column.setDescription("");
          tableAsset.addColumnsItem(column);
        }
      } catch (SQLException fallbackException) {
        log.error("Could not extract columns for table {}.{}: {}", databaseName, tableName,
            fallbackException.getMessage());
      }
    }
  }

  private Long getLastUpdatedAt() {
    Map<String, Object> state = stateRepository.getState();
    return (Long) state.getOrDefault("lastUpdatedAt", 0L);
  }

  private void setLastUpdatedAt(Long timestamp) {
    Map<String, Object> state = Map.of("lastUpdatedAt", timestamp);
    stateRepository.saveState(state);
  }

  /**
   * Sanitizes SQL identifiers (database names, table names) to prevent SQL injection.
   * Note: Database and table names cannot be parameterized in prepared statements as they are identifiers,
   * not values, so we must sanitize them by allowing only safe characters.
   */
  private String sanitizeIdentifier(String identifier) {
    if (identifier == null) {
      throw new IllegalArgumentException("Identifier cannot be null");
    }

    // Allow only alphanumeric characters, underscores, and hyphens
    // Remove any other characters that could be used for SQL injection
    String sanitized = identifier.replaceAll("[^a-zA-Z0-9_-]", "");

    if (sanitized.isEmpty()) {
      throw new IllegalArgumentException("Identifier cannot be empty after sanitization: " + identifier);
    }

    if (sanitized.length() > 128) {
      throw new IllegalArgumentException("Identifier too long after sanitization: " + sanitized);
    }

    return sanitized;
  }

  /**
   * Creates a safely quoted identifier for SQL queries.
   * Uses backticks for Hive compatibility.
   */
  private String quoteIdentifier(String identifier) {
    String sanitized = sanitizeIdentifier(identifier);
    return "`" + sanitized + "`";
  }

  private void parseDetailedTableInformation(String tableInfo, Asset tableAsset) {
    if (tableInfo == null || tableInfo.trim().isEmpty()) {
      log.debug("No detailed table information to parse");
      return;
    }

    DetailedTableInfoMode mode = properties.assets().detailedTableInfo();
    if (mode == null) {
      mode = DetailedTableInfoMode.JSON; // Default fallback
    }

    switch (mode) {
      case JSON:
        try {
          String jsonString = convertHiveObjectToJson(tableInfo);
          JsonNode json = objectMapper.readTree(jsonString);
          tableAsset.putPropertiesItem("detailedTableInfo", json);
          log.debug("Successfully parsed detailed table information as JSON");
        } catch (Exception e) {
          log.warn("Failed to parse detailed table information as JSON: {}", e.getMessage());
          // Fallback to raw if JSON parsing fails
          tableAsset.putPropertiesItem("detailedTableInfoRaw", tableInfo);
        }
        break;
      case RAW:
        tableAsset.putPropertiesItem("detailedTableInfoRaw", tableInfo);
        log.debug("Added detailed table information as raw string");
        break;
      case IGNORE:
        log.debug("Ignoring detailed table information as per configuration");
        // Do nothing - ignore the detailed table information
        break;
    }
  }

  String convertHiveObjectToJson(String hiveObject) {
    return new HiveObjectParser().convertToJson(hiveObject);
  }

  /**
   * Gets the configured ID prefix, with fallback to default.
   */
  private String getIdPrefix() {
    String prefix = properties.assets().idPrefix();
    if (prefix == null || prefix.trim().isEmpty()) {
      prefix = "hive-" + getHost();
    }
    return prefix;
  }

  /**
   * Gets the configured owner, returns null if not configured or empty.
   */
  private String getDefaultOwner() {
    String owner = properties.assets().owner();
    if (owner == null || owner.trim().isEmpty()) {
      return null;
    }
    return owner;
  }

  /**
   * Generates a consistent asset ID for a database.
   */
  private String getDatabaseAssetId(String databaseName) {
    return getIdPrefix() + "." + databaseName;
  }

  /**
   * Generates a consistent asset ID for a table using database name as schema name.
   * This is a convenience method for Hive where database == schema.
   */
  private String getTableAssetId(String databaseName, String tableName) {
    return getIdPrefix() + "." + databaseName + "." + tableName;
  }

  private String toJson(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      log.warn("Failed to convert object to JSON: {}", e.getMessage());
      return object.toString();
    }
  }
}
