package tqmc.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

public class ConversionUtils {

  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
          .toFormatter();

  public static final DateTimeFormatter FORMATTER_NO_DASH = DateTimeFormatter.ofPattern("yyyyMMdd");

  public static final DateTimeFormatter YYYY_MM_DD_DASH_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  public static final DateTimeFormatter DATE_FORMATTER_WITH_SLASHES =
      new DateTimeFormatterBuilder().appendPattern("MM/dd/yyyy").toFormatter();

  public static LocalDate getLocalDateFromDate(Date date) {
    if (date == null) {
      return null;
    }
    return Instant.ofEpochMilli(date.getTime()).atZone(ZoneOffset.systemDefault()).toLocalDate();
  }

  public static String getLocalDateStringFromDate(Date date) {
    LocalDate ld = getLocalDateFromDate(date);
    if (ld == null) {
      return null;
    }
    return DATE_FORMATTER.format(ld);
  }

  public static String getLocalDateString(LocalDate ld) {
    if (ld == null) {
      return null;
    }
    return DATE_FORMATTER.format(ld);
  }

  public static String getLocalDateStringNoDash(LocalDate ld) {
    if (ld == null) {
      return null;
    }
    return FORMATTER_NO_DASH.format(ld);
  }

  public static String getLocalDateStringFromLocalDateSlashes(LocalDate date) {
    return DATE_FORMATTER_WITH_SLASHES.format(date);
  }

  public static String getLocalDateStringFromDateSlashes(Date date) {
    LocalDate ld = getLocalDateFromDate(date);
    if (ld == null) {
      return null;
    }
    return DATE_FORMATTER_WITH_SLASHES.format(ld);
  }

  public static LocalDateTime getLocalDateTimeFromTimestamp(Timestamp ts) {
    if (ts == null) {
      return null;
    }
    return Instant.ofEpochMilli(ts.getTime()).atZone(ZoneOffset.systemDefault()).toLocalDateTime();
  }

  public static String getLocalDateTimeStringFromTimestamp(Timestamp ts) {
    LocalDateTime ldt = getLocalDateTimeFromTimestamp(ts);
    return getLocalDateTimeString(ldt);
  }

  public static String getLocalDateTimeString(LocalDateTime ldt) {
    if (ldt == null) {
      return null;
    }
    return DATE_TIME_FORMATTER.format(ldt);
  }

  public static LocalDateTime getUTCFromLocalNow() {
    LocalDateTime now = LocalDateTime.now();
    ZonedDateTime utcTimestamp =
        now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"));
    return utcTimestamp.toLocalDateTime();
  }

  public static String getStringUTCFromLocalNow() {
    return getLocalDateStringFromLocalDateSlashes(getUTCFromLocalNow().toLocalDate());
  }

  public static long getDaysBetween(LocalDateTime dateTime1, LocalDateTime dateTime2) {
    return ChronoUnit.DAYS.between(dateTime1.toLocalDate(), dateTime2.toLocalDate());
  }
}
