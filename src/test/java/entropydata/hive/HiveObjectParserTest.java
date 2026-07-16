package entropydata.hive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HiveObjectParserTest {

    private HiveObjectParser parser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        parser = new HiveObjectParser();
    }

    @Test
    void testConvertToJson() throws Exception {
        // Given - the example Hive data
        String hiveData = "Table(tableName:hive_example, dbName:default, owner:hive, createTime:1756241942, lastAccessTime:0, retention:0, sd:StorageDescriptor(cols:[FieldSchema(name:a, type:string, comment:null), FieldSchema(name:b, type:int, comment:null)], location:file:/opt/hive/data/warehouse/hive_example, inputFormat:org.apache.hadoop.mapred.TextInputFormat, outputFormat:org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat, compressed:false, numBuckets:-1, serdeInfo:SerDeInfo(name:null, serializationLib:org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe, parameters:{serialization.format=1}), bucketCols:[], sortCols:[], parameters:{}, skewedInfo:SkewedInfo(skewedColNames:[], skewedColValues:[], skewedColValueLocationMaps:{}), storedAsSubDirectories:false), partitionKeys:[FieldSchema(name:c, type:int, comment:null)], parameters:{external.table.purge=TRUE, totalSize=12, EXTERNAL=TRUE, numRows=3, rawDataSize=9, COLUMN_STATS_ACCURATE={\\\\\\\"BASIC_STATS\\\\\\\":\\\\\\\"true\\\\\\\"}, numPartitions=1, numFiles=1, TRANSLATED_TO_EXTERNAL=TRUE, transient_lastDdlTime=1756241942, bucketing_version=2, numFilesErasureCoded=0}, viewOriginalText:null, viewExpandedText:null, tableType:EXTERNAL_TABLE, rewriteEnabled:false, catName:hive, ownerType:USER, writeId:0, accessType:8, id:1)";

        // Expected JSON structure - full structure without class name wrappers
        String expectedJson = """
        {
          "tableName": "hive_example",
          "dbName": "default",
          "owner": "hive",
          "createTime": 1756241942,
          "lastAccessTime": 0,
          "retention": 0,
          "sd": {
            "cols": [
              {
                "name": "a",
                "type": "string",
                "comment": null
              },
              {
                "name": "b",
                "type": "int",
                "comment": null
              }
            ],
            "location": "file:/opt/hive/data/warehouse/hive_example",
            "inputFormat": "org.apache.hadoop.mapred.TextInputFormat",
            "outputFormat": "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat",
            "compressed": false,
            "numBuckets": -1,
            "serdeInfo": {
              "name": null,
              "serializationLib": "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe",
              "parameters": {
                 "serialization.format": 1
              }
            },
            "bucketCols": [],
            "sortCols": [],
            "parameters": {},
            "skewedInfo": {
              "skewedColNames": [],
              "skewedColValues": [],
              "skewedColValueLocationMaps": {}
            },
            "storedAsSubDirectories": false
          },
          "partitionKeys": [
            {
              "name": "c",
              "type": "int",
              "comment": null
            }
          ],
          "parameters": {
            "external.table.purge": "TRUE",
            "totalSize": 12,
            "EXTERNAL": "TRUE",
            "numRows": 3,
            "rawDataSize": 9,
            "COLUMN_STATS_ACCURATE": {
              "BASIC_STATS": "true"
            },
            "numPartitions": 1,
            "numFiles": 1,
            "TRANSLATED_TO_EXTERNAL": "TRUE",
            "transient_lastDdlTime": 1756241942,
            "bucketing_version": 2,
            "numFilesErasureCoded": 0
          },
          "viewOriginalText": null,
          "viewExpandedText": null,
          "tableType": "EXTERNAL_TABLE",
          "rewriteEnabled": false,
          "catName": "hive",
          "ownerType": "USER",
          "writeId": 0,
          "accessType": 8,
          "id": 1
        }
        """;

        // When
        String result = parser.convertToJson(hiveData);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());

        System.out.println("Raw conversion result: " + result);

        // Verify it's valid JSON
        JsonNode actualJson = objectMapper.readTree(result);
        JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);

        assertNotNull(actualJson);

        // Assert the full JSON structure matches
        assertEquals(expectedJsonNode, actualJson);

        System.out.println("Actual JSON: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actualJson));
        System.out.println("Expected JSON: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(expectedJsonNode));
    }

    @Test
    void testConvertToJson_withNullInput() throws Exception {
        // Given
        String hiveData = null;

        // When
        String result = parser.convertToJson(hiveData);

        // Then
        assertEquals("{}", result);
    }

    @Test
    void testConvertToJson_withSimpleObject() throws Exception {
        // Given
        String hiveData = "SimpleObject(name:test, value:123, active:true)";

        // Expected
        String expectedJson = """
        {
          "name": "test",
          "value": 123,
          "active": true
        }
        """;

        // When
        String result = parser.convertToJson(hiveData);

        // Then
        JsonNode actualJson = objectMapper.readTree(result);
        JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);

        assertEquals(expectedJsonNode, actualJson);
    }

    @Test
    void testConvertToJson_withNestedObjects() throws Exception {
        // Given
        String hiveData = "Parent(child:Child(name:test, value:456), count:10)";

        // Expected
        String expectedJson = """
        {
          "child": {
            "name": "test",
            "value": 456
          },
          "count": 10
        }
        """;

        // When
        String result = parser.convertToJson(hiveData);

        // Then
        JsonNode actualJson = objectMapper.readTree(result);
        JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);

        assertEquals(expectedJsonNode, actualJson);
    }

    @Test
    void testConvertToJson_withEscapedQuotes() throws Exception {
        // Given - Hive data with escaped backslashed quotes
        String hiveData = "TestObject(stats:{\\\\\\\"BASIC_STATS\\\\\\\":\\\\\\\"true\\\\\\\"}, name:test)";

        // Expected - quotes should be normalized
        String expectedJson = """
        {
          "stats": {
            "BASIC_STATS": "true"
          },
          "name": "test"
        }
        """;

        // When
        String result = parser.convertToJson(hiveData);

        // Then
        JsonNode actualJson = objectMapper.readTree(result);
        JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);

        assertEquals(expectedJsonNode, actualJson);
    }

    @Test
    void testConvertToJson_withArrays() throws Exception {
        // Given
        String hiveData = "Container(items:[Item(name:a, id:1), Item(name:b, id:2)], size:2)";

        // Expected
        String expectedJson = """
        {
          "items": [
            {
              "name": "a",
              "id": 1
            },
            {
              "name": "b",
              "id": 2
            }
          ],
          "size": 2
        }
        """;

        // When
        String result = parser.convertToJson(hiveData);

        // Then
        JsonNode actualJson = objectMapper.readTree(result);
        JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);

        assertEquals(expectedJsonNode, actualJson);
    }
}
