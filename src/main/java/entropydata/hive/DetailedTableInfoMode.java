package entropydata.hive;

/**
 * Enumeration for handling detailed table information from DESCRIBE EXTENDED.
 */
public enum DetailedTableInfoMode {
    /**
     * Parse the detailed table information as JSON using HiveObjectParser.
     */
    JSON,
    
    /**
     * Include the detailed table information as raw string data.
     */
    RAW,
    
    /**
     * Ignore the detailed table information completely.
     */
    IGNORE
}