package tqmc.domain.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import org.apache.commons.text.StringEscapeUtils;

public class CustomMapper extends SimpleModule {

  private static final long serialVersionUID = 7508060578903475428L;

  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
          .toFormatter();

  public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  // used to translate incoming noun store to a desired object type
  // incoming arguments are loaded into map with form {String: Vector<Object>, String:
  // Vector<Object>}
  // mapping to a class serializes that map, then deserializes to the object
  public static final ObjectMapper PAYLOAD_MAPPER =
      JsonMapper.builder()
          .enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
          .build()
          .registerModule(new CustomMapper());

  // used to translate an object type to a result map
  // object is serialized and deserialized to map
  public static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
          .build()
          .registerModule(new CustomMapper());

  public CustomMapper() {

    addDeserializer(
        String.class,
        new StdDeserializer<String>(String.class) {
          private static final long serialVersionUID = -4179939507094287588L;

          @Override
          public String deserialize(final JsonParser jp, final DeserializationContext ctxt)
              throws IOException, JsonProcessingException {
            String val = jp.getText();
            if (val == null) {
              return null;
            }
            return StringEscapeUtils.unescapeJson(val);
          }
        });

    addDeserializer(
        LocalDate.class,
        new StdDeserializer<LocalDate>(LocalDate.class) {
          private static final long serialVersionUID = -2185544939872398484L;

          @Override
          public LocalDate deserialize(final JsonParser jp, final DeserializationContext ctxt)
              throws IOException, JsonProcessingException {
            String date = jp.getText();
            if (date == null) {
              return null;
            }

            date = date.trim();
            if (date.isEmpty()) {
              return null;
            }

            try {
              return LocalDate.from(DATE_FORMATTER.parse(date));
            } catch (final DateTimeException e) {
              throw new RuntimeException(e);
            }
          }
        });

    addDeserializer(
        Long.class,
        new StdDeserializer<Long>(Long.class) {
          private static final long serialVersionUID = 1304217665668778072L;

          @Override
          public Long deserialize(final JsonParser jp, final DeserializationContext ctxt)
              throws IOException, JsonProcessingException {
            String num = jp.getText();
            if (num == null) {
              return null;
            }

            num = num.trim();
            if (num.isEmpty()) {
              return null;
            }

            try {
              return Long.parseLong(num);
            } catch (final NumberFormatException e) {
              throw new RuntimeException(e);
            }
          }
        });

    addSerializer(
        LocalDate.class,
        new StdSerializer<LocalDate>(LocalDate.class) {
          private static final long serialVersionUID = -1432265864622998584L;

          @Override
          public void serialize(
              final LocalDate value, final JsonGenerator gen, final SerializerProvider arg2)
              throws IOException, JsonProcessingException {
            if (value == null) {
              gen.writeNull();
            } else {
              gen.writeString(DATE_FORMATTER.format(value));
            }
          }
        });

    addDeserializer(
        LocalDateTime.class,
        new StdDeserializer<LocalDateTime>(LocalDateTime.class) {
          private static final long serialVersionUID = -2733098095827201269L;

          @Override
          public LocalDateTime deserialize(final JsonParser jp, final DeserializationContext ctxt)
              throws IOException, JsonProcessingException {
            String date = jp.getText();
            if (date == null) {
              return null;
            }

            date = date.trim();
            if (date.isEmpty()) {
              return null;
            }

            try {
              return LocalDateTime.from(DATE_TIME_FORMATTER.parse(date));
            } catch (final DateTimeException e) {
              throw new RuntimeException(e);
            }
          }
        });

    addSerializer(
        LocalDateTime.class,
        new StdSerializer<LocalDateTime>(LocalDateTime.class) {
          private static final long serialVersionUID = 5112049791879435813L;

          @Override
          public void serialize(
              final LocalDateTime value, final JsonGenerator gen, final SerializerProvider arg2)
              throws IOException, JsonProcessingException {
            if (value == null) {
              gen.writeNull();
            } else {
              gen.writeString(DATE_TIME_FORMATTER.format(value));
            }
          }
        });

    addSerializer(
        Long.class,
        new StdSerializer<Long>(Long.class) {
          private static final long serialVersionUID = 4421370263036817259L;

          @Override
          public void serialize(
              final Long value, final JsonGenerator gen, final SerializerProvider arg2)
              throws IOException, JsonProcessingException {
            if (value == null || value.equals(0L)) {
              gen.writeNull();
            } else {
              gen.writeString(value.toString());
            }
          }
        });
  }
}
