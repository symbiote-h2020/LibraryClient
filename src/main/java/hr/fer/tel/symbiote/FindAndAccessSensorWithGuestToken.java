package hr.fer.tel.symbiote;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

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

public class FindAndAccessSensorWithGuestToken {
  private String symbioteCoreUrl = "https://symbiote-open.man.poznan.pl";
  private String keystorePath = "testKeystore.jks"; // this app keystore credentials
  private String keystorePassword = "testPass";

  private String platformId = "DemoPlatform2";

  private AbstractSymbIoTeClientFactory clientFactory;

  public FindAndAccessSensorWithGuestToken() throws SecurityHandlerException, NoSuchAlgorithmException {
    Config config = new Config(symbioteCoreUrl, keystorePath, keystorePassword, Type.FEIGN);
    clientFactory = AbstractSymbIoTeClientFactory.getFactory(config);
  }

  public static void main(String[] args) throws SecurityHandlerException, IOException, NoSuchAlgorithmException {
    new FindAndAccessSensorWithGuestToken().run();
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
        .platformId(platformId)
        .resourceType("StationarySensor") // http://www.symbiote-h2020.eu/ontology/core#StationarySensor
        //.name("ProtectedSensor1")
        .build();

    SearchClient searchClient = clientFactory.getSearchClient();
    QueryResponse searchResult = searchClient.searchAsGuest(queryRequest, true);
    System.out.println("searchResult: " + searchResult.getBody());

    System.out.println("Found " + searchResult.getBody().size() + " resources");
    // choose one resource
    QueryResourceResult resource = searchResult.getBody().stream()
        .findFirst()
        .get();
    return resource;
  }

  private String getResourceUrl(String resourceId) {
    System.out.println("*** get url");

    CRAMClient cramClient = clientFactory.getCramClient();
    ResourceUrlsResponse resourceUrlsResponse = cramClient.getResourceUrlAsGuest(resourceId, true);
    String resourceUrl = resourceUrlsResponse.getBody().get(resourceId);

    System.out.println("URL: " + resourceUrl);
    return resourceUrl;
  }

  private Observation accessResource(String resourceUrl) {
    System.out.println("*** get data");
    RAPClient rapClient = clientFactory.getRapClient();
    Observation observation = rapClient.getLatestObservationAsGuest(resourceUrl, true);
    return observation;
  }
}
