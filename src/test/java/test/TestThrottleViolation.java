package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TestThrottleViolation {
  
  private static final String APP_KEY = "10Dhx17f)ejWEM)gWsiPvw((";
  private static final String URL_PATTERN = "https://api.stackexchange.com/2.2/questions?filter=!2rt.po)yToP4&"
      + "site=%s&"
      + "tagged=%s&"
      + "fromDate=%s&"
      + "toDate=%s&"
      + "pageSize=100&"
      + "page=1&"
      + "key=%s";

  @Test
  public void test() throws Exception {
    List<String> tags = readTags();
    List<MonthPeriod> months = getMonths(2012);
    int count = 0;
    for (int i = 0; i < 3; i++) {
      MonthPeriod month = months.get(i);
      for (String tag : tags) {
        System.out.println("Call # " + count++);
        String url = String.format(URL_PATTERN, "superuser", tag, month.getFrom(), month.getTo(), APP_KEY);
        callURL(url);
      }
    }
  }
  
  protected boolean callURL(String urlStr) throws IOException, InterruptedException {
    logURL(urlStr);
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    InputStream inputStream = null;
    
    try {
      inputStream = conn.getInputStream();
    } catch (IOException e) {
      inputStream = conn.getErrorStream();
    }
    inputStream = new GZIPInputStream(inputStream);
    try {
      String response = intpuStreamToString(inputStream);
      logResponseBody(response);
      handleBackoff(response);
      if (conn.getResponseCode() != 200) {
        throw new IOException("Received error response code: " + conn.getResponseCode());
      }
      Thread.sleep(1000);
    } finally {
      inputStream.close();
      conn.disconnect();
    }
    return true;
  }



  private String intpuStreamToString(InputStream inputStream) throws IOException {
    StringBuilder result = new StringBuilder();
    byte[] buff = new byte[1024];
    int count = -1; 
    while ((count = inputStream.read(buff, 0, buff.length)) != -1) {
      result.append(new String(buff, 0, count, Charset.forName("UTF-8")));
    }
    String response = result.toString();
    return response;
  }

  protected void handleBackoff(String response) {
    JsonObject responseJson = getAsJsonObject(response);
    JsonElement backoffElement = responseJson.get("backoff");
    if (backoffElement != null) {
      int backoffSeconds = backoffElement.getAsInt();
      System.out.println("Received backoff from StackExchange. Waiting for " + backoffSeconds + " seconds.");
      try {
        Thread.sleep(backoffSeconds*1000);
      } catch (InterruptedException e) {
      }
    }
  }

  private JsonObject getAsJsonObject(String respBody) {
    JsonParser parser = new JsonParser();
    JsonObject root = parser.parse(respBody).getAsJsonObject();
    return root;
  }

  
  private List<String> readTags() throws IOException {
    List<String> toReturn = new ArrayList<>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("tags.txt")));
    String line = null;
    while ((line = reader.readLine()) != null) {
      toReturn.add(line);
    }
    return toReturn;
  }

  
  public List<MonthPeriod> getMonths(int year) {
    List<MonthPeriod> days = new ArrayList<>();
    Year y = Year.of(year);
    for (int i = 1; i <= 12; i++) {
      days.add(getMonthPeriod(year, y.atMonth(i)));
    }

    return days;
  }

  private MonthPeriod getMonthPeriod(int year, YearMonth month) {
    ZonedDateTime date = LocalDate.of(year, month.getMonth(), 1).atStartOfDay(ZoneId.of("UTC"));
    long from = date.toInstant().getEpochSecond();
    long to = date.plusMonths(1).toInstant().getEpochSecond();
    return new MonthPeriod(from, to);
  }

  private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

  protected static void logResponseBody(String respBody) {
    
    System.out.println("[" + dtf.format(LocalDateTime.now()) + "] Response body received: ");
    System.out.println(respBody);
  }


  private static void logURL(String url) {
    System.out.println("[" + dtf.format(LocalDateTime.now()) + "] Calling URL: " + url);
  }
  
  static class MonthPeriod {
    private long from;
    private long to;

    public MonthPeriod(long from, long to) {
      super();
      this.from = from;
      this.to = to;
    }

    public long getFrom() {
      return from;
    }

    public long getTo() {
      return to;
    }
  }
}
