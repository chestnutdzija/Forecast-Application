import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


/**
 * ForecastApp.java - a simple class for demonstrating access to NOAA REST web services.
 * @author chestnut.dzija
 * @version 1.0
 * @see NOA Web page https://www.weather.gov/documentation/services-web-api
 * @see Points API https://api.weather.gov/points/{latitude},{longitude}
 * @see Forecast API https://api.weather.gov/gridpoints/{office}/{grid X},{grid Y}/forecast
 */

public class ForecastApp {
    private static final String USER_AGENT = "Mozilla";

    private static final String GET_URL = "https://api.weather.gov/points/39.7456,-97.0892";
    private static final String POINTS_QUERY_PREFIX = "https://api.weather.gov/points/";
    private static final String FORECAST_QUERY_PREFIX = "https://api.weather.gov/gridpoints/";
    private static final String GRID_X = "gridX";
    private static final String GRID_Y = "gridY";
    private static final String GRID_ID = "gridId";

    private static final boolean debugFlag = false;

    public static void main(String[] args) throws IOException {

        // We must provide two and only two arguments
        if (args.length != 2) {
            usage();
            System.exit(1);
        }

        // The first argument must be -l/-L
        if (!"-l".equalsIgnoreCase(args[0])) {
            usage();
            System.exit(1);
        }

        // Make sure latitude/longitude conform to the form NOAA can understand
        if (!args[1].matches("^(-?\\d+(\\.\\d+)?),(-?.\\d+(\\.\\d+)?)$")) {
            System.out.println("\n\n\t***** lat,lon doesn't match the standard form. Please make lat,long conform the \"standard\" form.\n");
            usage();
            System.exit(1);
        }

        String latLong = args[1];
        debug("Getting points for " + latLong);
        String jsonPoints = getPoints(latLong);
        if (jsonPoints.length() == 0) {
            System.exit(1);
        }

        final ObjectNode node = new ObjectMapper().readValue(jsonPoints, ObjectNode.class);

        int gridX = getGridValue(node, GRID_X);
        int gridY = getGridValue(node, GRID_Y);
        debug("gridX = " + gridX + " gridY = " + gridY);

        String office = getOffice(node, GRID_ID);
        debug("gridId = " + office);

        String forecastQuery = buildForecastQuery(office, gridX, gridY);
        String forecast = getForecast(forecastQuery);
        if (forecast.length() == 0) {
            System.exit(1);
        }
        debug("FORECAST: \n\n" + forecast);

        ObjectNode forecastNode = new ObjectMapper().readValue(forecast, ObjectNode.class);
        // Tomorrow's forecast starts from period 3 (periods are 1 based)
        JsonNode periods = forecastNode.get("properties").get("periods");
        debug("\n   ========>  periods length = " + periods.size());

        prettyPrint(periods);
    }

    // Pretty print the forecast
    private static void prettyPrint(JsonNode periods) {
        // First figure out which is the the first period ("Today"/"Tonight"),
        // since if it's "Tonight", we have to start from tomorrow's forecast
        int startPeriod = getFirstPeriod(periods);
        int forecastDays = 5; // we need to see next 5 days

        // Pretty print forecast for next forecastDays
        System.out.println("Detailed forecast for next " + forecastDays + " days");
        System.out.println("=================================");
        for (int i = startPeriod; i < startPeriod + (2 * forecastDays); i++) {
            Period period = getDetailedForecastForPeriod(periods.get(i));
            System.out.println("\t" + period.name + ":");
            System.out.println("\t" + period.detailedForecast);
            period = getDetailedForecastForPeriod(periods.get(i + 1));
            System.out.println("\t" + period.name + ":");
            System.out.println("\t" + period.detailedForecast);
            System.out.print("\t");
            for (int j = 0; j < period.detailedForecast.length(); j++) {
                System.out.print("-");
            }
            System.out.println("\n");
        }
    }

    private static String buildForecastQuery(String office, int gridX, int gridY) {
        return FORECAST_QUERY_PREFIX + office + "/" + gridX + "," + gridY + "/forecast";
    }

    // Get gridX/gridY values
    private static int getGridValue(ObjectNode node, String grid) {
        JsonNode gridNode = node.get("properties").get(grid);
        if (gridNode.isInt()) {
            debug("==============> " + grid + ": " + gridNode.intValue());
            return gridNode.intValue();
        }

        return 0;
    }

    // Get office (gridId) value
    private static String getOffice(ObjectNode node, String property) {
        JsonNode propNode = node.get("properties").get(property);
        if (propNode.isTextual()) {
            debug("==============> " + property + ": " + propNode.textValue());
            return propNode.textValue();
        }

        return null;
    }

    // Get the current period - it's either "Today" or "Tonight".
    // If it's "Tonight", our first forecast is tomorrow's day.
    private static int getFirstPeriod(JsonNode periods) {
        JsonNode firstNode = periods.get(0);
        String name = firstNode.get("name").textValue();
        if ("Today".equalsIgnoreCase(name)) {
            return 2;
        }

        return 1; // must be "Tonight"
    }

    private static String getPoints(String latLong) throws IOException {
        URL obj = new URL(POINTS_QUERY_PREFIX + latLong);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);
        int responseCode = con.getResponseCode();
        StringBuffer response = new StringBuffer();
        if (responseCode == HttpURLConnection.HTTP_OK) { // success
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        } else {
            System.out.println("GET points request failed");
            System.out.println("Response code: " + responseCode);
        }

        return response.toString();
    }

    private static String getForecast(String forecastQuery) throws IOException {
        URL obj = new URL(forecastQuery);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);
        int responseCode = con.getResponseCode();
        StringBuffer response = new StringBuffer();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        } else {
            System.out.println("GET forecast request failed");
        }

        return response.toString();
    }

    private static Period getDetailedForecastForPeriod(JsonNode forecastNode) {
        if (forecastNode.get("detailedForecast").isTextual()) {
            debug("==============> " + forecastNode.get("detailedForecast").textValue());
            return new Period(forecastNode.get("name").textValue(),
                    forecastNode.get("detailedForecast").textValue());
        }

        return null;
    }

    private static void usage() {
        System.out.println("\nProgram usage:\n");
        System.out.println("\tjava ForecastApp -l lat,lon\n");
        System.out.println("\tWhere lat,lon is of the form: 39.7456,-97.0892\n");
        System.out.println("\tPlease fix the problem and run the program again.\n");
    }

    private static void debug(String txt) {
        if (debugFlag) {
            System.out.println(txt);
        }
    }
}

// Helper POJO class
class Period {
    public String name; // Tonight, Monday, Monday Night, ...
    public String detailedForecast;

    Period(String name, String detailedForecast) {
        this.name = name;
        this.detailedForecast = detailedForecast;
    }
}

