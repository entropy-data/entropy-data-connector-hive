package entropydata.hive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for Hive object notation format.
 *
 * Converts Hive object strings like:
 * Table(tableName:value, field:Constructor(subfield:value))
 *
 * Into clean JSON objects:
 * {"tableName": "value", "field": {"subfield": "value"}}
 */
public class HiveObjectParser {

  private static final Logger log = LoggerFactory.getLogger(HiveObjectParser.class);

  public String convertToJson(String hiveObject) {
    if (hiveObject == null) {
      return "{}";
    }

    try {
      // Step 1: Normalize escaped backslashed quotes to regular quotes
      String result = normalizeEscapedQuotes(hiveObject);
      
      // Step 2: Replace Xxx( with { and ) with }
      result = result.replaceAll("[A-Za-z][A-Za-z0-9.]*\\(", "{");
      result = result.replaceAll("\\)", "}");

      // Step 3: Replace = with : (but not inside already quoted strings)
      result = replaceEqualsWithColon(result);

      // Step 4: Add quotes around keys and unquoted values
      result = addQuotesImproved(result);

      return result;
    } catch (Exception e) {
      log.warn("JSON conversion failed, storing as raw data: {}", e.getMessage());
      return "{\"rawData\": \"" + hiveObject.replace("\"", "\\\"") + "\"}";
    }
  }

  /**
   * Normalize escaped backslashed quotes to regular quotes.
   * Converts patterns like \\\\\\\" to "
   */
  private String normalizeEscapedQuotes(String input) {
    // Replace multiple backslashes followed by a quote with just a quote
    // This handles cases like \\\\\" or \\\" -> "
    return input.replaceAll("\\\\+\"", "\"");
  }

  /**
   * Replace = with : but not inside already quoted strings
   */
  private String replaceEqualsWithColon(String input) {
    StringBuilder result = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);

      if (c == '"' && (i == 0 || input.charAt(i - 1) != '\\')) {
        inQuotes = !inQuotes;
        result.append(c);
      } else if (c == '=' && !inQuotes) {
        result.append(':');
      } else {
        result.append(c);
      }
    }

    return result.toString();
  }

  /**
   * Improved method to add quotes - handles key:value pairs properly
   */
  private String addQuotesImproved(String input) {
    StringBuilder result = new StringBuilder();
    boolean inQuotes = false;
    int i = 0;

    while (i < input.length()) {
      char c = input.charAt(i);

      if (c == '"') {
        inQuotes = !inQuotes;
        result.append(c);
        i++;
      } else if (!inQuotes && (c == '{' || c == '[')) {
        result.append(c);
        i++;
      } else if (!inQuotes && (c == '}' || c == ']')) {
        result.append(c);
        i++;
      } else if (!inQuotes && c == ':') {
        result.append(c);
        i++;
        // Skip whitespace after colon
        while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
          result.append(input.charAt(i));
          i++;
        }
        // Now quote the value if needed
        if (i < input.length() && input.charAt(i) != '"' && input.charAt(i) != '{' && input.charAt(i) != '[') {
          int valueStart = i;
          while (i < input.length() && input.charAt(i) != ',' && input.charAt(i) != '}' && input.charAt(i) != ']') {
            i++;
          }
          String value = input.substring(valueStart, i).trim();
          if ("true".equals(value) || "false".equals(value) || "null".equals(value) || isNumber(value)) {
            result.append(value);
          } else {
            result.append('"').append(value).append('"');
          }
        }
      } else if (!inQuotes && (Character.isLetter(c) || c == '_')) {
        // This might be a key - quote it (include dots, underscores)
        int keyStart = i;
        while (i < input.length() && (Character.isLetterOrDigit(input.charAt(i)) ||
               input.charAt(i) == '.' || input.charAt(i) == '_')) {
          i++;
        }
        String key = input.substring(keyStart, i);
        result.append('"').append(key).append('"');
      } else {
        result.append(c);
        i++;
      }
    }

    return result.toString();
  }

  private boolean isNumber(String str) {
    if (str == null || str.isEmpty()) return false;
    try {
      Double.parseDouble(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
