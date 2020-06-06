package hr.fer.tel.symbiote;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import eu.h2020.symbiote.client.AbstractSymbIoTeClientFactory;
import eu.h2020.symbiote.client.AbstractSymbIoTeClientFactory.Config;
import eu.h2020.symbiote.client.AbstractSymbIoTeClientFactory.Type;
import eu.h2020.symbiote.client.interfaces.CRAMClient;
import eu.h2020.symbiote.client.interfaces.RAPClient;
import eu.h2020.symbiote.client.interfaces.SearchClient;
import eu.h2020.symbiote.core.ci.QueryResourceResult;
import eu.h2020.symbiote.core.ci.QueryResponse;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.core.internal.cram.ResourceUrlsResponse;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.security.commons.exceptions.custom.SecurityHandlerException;

public class FindAndAccessSensorWithCloudUserButWithoutUserLogin {
  public static final boolean LOGGING = true;

  private static String symbioteCoreUrl = "https://symbiote-open.man.poznan.pl";
  private static String keystorePath = "testKeystore.jks"; // this app keystore credentials
  private static String keystorePassword = "testPass";

  private static String homePlatformId = "DemoPlatform2";
  private String username;
  private String password;

  private AbstractSymbIoTeClientFactory clientFactory;

  public static void main(String[] args) throws SecurityHandlerException, IOException, NoSuchAlgorithmException {
    new FindAndAccessSensorWithCloudUserButWithoutUserLogin().run();
  }

  public FindAndAccessSensorWithCloudUserButWithoutUserLogin() throws SecurityHandlerException, NoSuchAlgorithmException {
    Config config = new Config(symbioteCoreUrl, keystorePath, keystorePassword, Type.FEIGN);
    clientFactory = AbstractSymbIoTeClientFactory.getFactory(config);
  }

  private void run() throws IOException {
    QueryResourceResult resource = searchCore();

    System.out.println("Resource: " + resource);
    String resourceId = resource.getId();

    String resourceUrl = getResourceUrl(resourceId);

    Observation observation = accessResource(resourceUrl);

    System.out.println("data received");
    observation.getObsValues().stream()
      .map(v -> v.getObsProperty().getName() + " = " + v.getValue() + " " + v.getUom().getSymbol())
      .forEach(System.out::println);
  }

  private QueryResourceResult searchCore() {
    System.out.println("*** search");

    CoreQueryRequest queryRequest = new CoreQueryRequest.Builder()
      .platformId(homePlatformId)
      .resourceType("StationarySensor")
      .name("ProtectedResource1")
      .build();

    SearchClient searchClient = clientFactory.getSearchClient();
    QueryResponse searchResult = searchClient.search(queryRequest, true, Set.of(homePlatformId));
    System.out.println("searchResult: " + searchResult.getBody());

    // choose one resource
    QueryResourceResult resource = searchResult.getBody().get(0);
    return resource;
  }

  private String getResourceUrl(String resourceId) {
    System.out.println("*** get url");

    CRAMClient cramClient = clientFactory.getCramClient();
    ResourceUrlsResponse resourceUrlsResponse = cramClient.getResourceUrl(resourceId, true, Set.of(homePlatformId));
    String resourceUrl = resourceUrlsResponse.getBody().get(resourceId);

    System.out.println("URL: " + resourceUrl);
    return resourceUrl;
  }

  private Observation accessResource(String resourceUrl) {
    System.out.println("*** get data");
    RAPClient rapClient = clientFactory.getRapClient();
    Observation observation = rapClient.getLatestObservation(resourceUrl, true, Set.of(homePlatformId));
    return observation;
  }
}
