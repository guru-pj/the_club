package com.theclub.clubservice.model;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores small collections as JSON text, matching the JSON columns in db_schema.sql.
 */
public final class JsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonConverters() {
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize value to JSON", e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot read JSON column", e);
        }
    }

    @Converter
    public static class StringMap implements AttributeConverter<Map<String, String>, String> {

        @Override
        public String convertToDatabaseColumn(Map<String, String> attribute) {
            return write(attribute == null ? Map.of() : attribute);
        }

        @Override
        public Map<String, String> convertToEntityAttribute(String dbData) {
            return dbData == null || dbData.isBlank()
                    ? new LinkedHashMap<>()
                    : read(dbData, new TypeReference<LinkedHashMap<String, String>>() { });
        }
    }

    @Converter
    public static class StringList implements AttributeConverter<List<String>, String> {

        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            return write(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            return dbData == null || dbData.isBlank()
                    ? new ArrayList<>()
                    : read(dbData, new TypeReference<ArrayList<String>>() { });
        }
    }
}
