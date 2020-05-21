package hr.fer.tel.symbiote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.core.ci.QueryResourceResult;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsResponse;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.model.cim.ObservationValue;
import eu.h2020.symbiote.security.ClientSecurityHandlerFactory;
import eu.h2020.symbiote.security.commons.Token;
import eu.h2020.symbiote.security.commons.credentials.AuthorizationCredentials;
import eu.h2020.symbiote.security.commons.credentials.HomeCredentials;
import eu.h2020.symbiote.security.commons.exceptions.custom.SecurityHandlerException;
import eu.h2020.symbiote.security.commons.exceptions.custom.ValidationException;
import eu.h2020.symbiote.security.communication.payloads.AAM;
import eu.h2020.symbiote.security.communication.payloads.SecurityRequest;
import eu.h2020.symbiote.security.handler.ISecurityHandler;
import eu.h2020.symbiote.security.helpers.MutualAuthenticationHelper;

public class FindAndAccessSensorWithCloudUser {
  public static boolean LOGGING = true;

  private String symbioteCoreUrl;
  private String keystorePath = "testKeystore"; // this app keystore credentials
  private String keystorePassword = "testPass";

  private ISecurityHandler securityHandler;

  private String homePlatformId;
  private String username;
  private String password;
  private String clientId;

  public FindAndAccessSensorWithCloudUser() throws SecurityHandlerException {
    symbioteCoreUrl = "https://symbiote-open.man.poznan.pl/coreInterface/";
    homePlatformId = "DemoPlatform2";
    // can not access anything
//    username = "friend";
//    password = "pass";

    // can only search working ---- THIS IS NOT WORKING :(
    username = "repairmanSearch";
    password = "pass";

    // can access anything
//    username = "parent";
//    password = "pass";

    clientId = "someClientId";

    securityHandler = ClientSecurityHandlerFactory.getSecurityHandler(symbioteCoreUrl, keystorePath,
        keystorePassword);

  }

  public static void main(String[] args) throws SecurityHandlerException, IOException {
    new FindAndAccessSensorWithCloudUser().run();
  }

  private void run() throws IOException {
    System.out.println("*** security");
    Map<String, String> requestHeaders = getSecurityRequestHeaders();
    System.out.println("Security request headers: " + requestHeaders);

    System.out.println("*** search");
    CoreQueryRequest queryRequest = new CoreQueryRequest();
    queryRequest.setPlatform_name("Demo Platform2");
    queryRequest.setName("ProtectedSensor1");

    QueryResponse searchResult = searchForResources(requestHeaders, queryRequest);
    System.out.println("searchResult: " + searchResult.getBody());

    // choose one resource
    QueryResourceResult resource = searchResult.getBody().stream()
      .filter(r -> r.getResourceType().stream()
        .filter(type -> type.toLowerCase().contains("sensor"))
        .anyMatch(s -> true))
      .findFirst()
      .get();

    System.out.println("Resource: " + resource);
    String resourceId = resource.getId();

    System.out.println("*** get url");
    String resourceURL = getResourceUrl(requestHeaders, resourceId);
    System.out.println("URL: " + resourceURL);

    System.out.println("*** get data");
    int noOfReadings = 2;
    List<ObservationValue> values = getReadCurrentValue(requestHeaders, resourceURL, noOfReadings);
    System.out.println("data received");
    values.stream()
      .map(v -> v.getObsProperty().getName() + " = " + v.getValue() + v.getUom().getSymbol())
      .forEach(System.out::println);
  }

  private List<ObservationValue> getReadCurrentValue(Map<String, String> requestHeaders, String resourceURL, int top)
      throws IOException {
    URL url = new URL(resourceURL + "/Observations?$top=" + top);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    setSecurityHeaders(requestHeaders, con);
    con.setRequestProperty("Accept", "application/json");

    con.connect();

    ObjectMapper mapper = new ObjectMapper();
    Reader reader = readAndLog(con.getInputStream());
    List<Observation> response = mapper.readValue(reader, new TypeReference<List<Observation>>() {
    });

    logInfo("data received1");
    return response.stream()
      .flatMap(o -> o.getObsValues().stream())
      .collect(Collectors.toList());

  }

  private String getResourceUrl(Map<String, String> requestHeaders, String resourceId) throws IOException {
    logInfo("Requesting url from CRAM for the resource with id " + resourceId);

    URL url = new URL(symbioteCoreUrl + "resourceUrls?id=" + resourceId);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    setSecurityHeaders(requestHeaders, con);

    con.connect();

    Reader reader = readAndLog(con.getInputStream());
    ObjectMapper mapper = new ObjectMapper();
    ResourceUrlsResponse response = mapper.readValue(reader, ResourceUrlsResponse.class);

    return response.getBody().get(resourceId);
  }

  private QueryResponse searchForResources(Map<String, String> requestHeaders, CoreQueryRequest queryRequest)
      throws IOException {
    String queryUrl = queryRequest.buildQuery(symbioteCoreUrl).replaceAll("#", "%23");
    logInfo("queryUrl = " + queryUrl);

    URL url = new URL(queryUrl);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    setSecurityHeaders(requestHeaders, con);

    con.connect();

    Reader reader = readAndLog(con.getInputStream());
    ObjectMapper mapper = new ObjectMapper();

    return mapper.readValue(reader, QueryResponse.class);
  }

  private void setSecurityHeaders(Map<String, String> requestHeaders, HttpURLConnection con) {
    requestHeaders.entrySet().stream()
      .forEach(e -> con.setRequestProperty(e.getKey(), e.getValue()));
  }

  private Map<String, String> getSecurityRequestHeaders() {
    // Insert Security Request into the headers
    try {

      Set<AuthorizationCredentials> authorizationCredentialsSet = new HashSet<>();
      Map<String, AAM> availableAAMs = securityHandler.getAvailableAAMs();

      logInfo("Getting certificate for " + availableAAMs.get(homePlatformId).getAamInstanceId());
      securityHandler.getCertificate(availableAAMs.get(homePlatformId), username, password, clientId);

      logInfo("Getting token from " + availableAAMs.get(homePlatformId).getAamInstanceId());
      Token homeToken = securityHandler.login(availableAAMs.get(homePlatformId));

      HomeCredentials homeCredentials = securityHandler.getAcquiredCredentials().get(homePlatformId).homeCredentials;
      authorizationCredentialsSet
        .add(new AuthorizationCredentials(homeToken, homeCredentials.homeAAM, homeCredentials));

      SecurityRequest securityRequest = MutualAuthenticationHelper.getSecurityRequest(authorizationCredentialsSet,
          false);
      return securityRequest.getSecurityRequestHeaderParams();

    } catch (SecurityHandlerException | ValidationException | JsonProcessingException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  protected static Reader readAndLog(InputStream inputStream) throws IOException {
    String result = readAndLogString(inputStream);
    return new StringReader(result);
  }

  protected static String callService(String securityRequest, String serviceURL, String payload) throws IOException {
    URL url = new URL(serviceURL);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("PUT");
    setSecurityHeaders(securityRequest, con);
    con.setRequestProperty("Accept", "application/json");
    con.setRequestProperty("Content-Type", "application/json");
    con.setDoOutput(true);
    con.connect();

    OutputStream outputStream = con.getOutputStream();
    outputStream.write(payload.getBytes());
    outputStream.flush();

    return readAndLogString(con.getInputStream());
  }

  protected static void setSecurityHeaders(String securityRequest, HttpURLConnection con) {
    con.setRequestProperty("x-auth-timestamp", Long.toString(System.currentTimeMillis()));
    con.setRequestProperty("x-auth-1", securityRequest);
    con.setRequestProperty("x-auth-size", "1");
  }

  protected static String readAndLogString(InputStream inputStream) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
    String temp = null;
    StringBuilder sb = new StringBuilder();
    while ((temp = in.readLine()) != null) {
      sb.append(temp).append(" ");
    }
    String result = sb.toString();
    logInfo("Body: " + result);
    return result;
  }

  private static void logInfo(String message) {
    if (LOGGING) {
      System.out.println(message);
    }
  }
}
