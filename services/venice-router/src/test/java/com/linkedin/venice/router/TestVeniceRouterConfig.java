package com.linkedin.venice.router;

import static com.linkedin.venice.ConfigKeys.CLUSTER_NAME;
import static com.linkedin.venice.ConfigKeys.CLUSTER_TO_D2;
import static com.linkedin.venice.ConfigKeys.KAFKA_BOOTSTRAP_SERVERS;
import static com.linkedin.venice.ConfigKeys.LISTENER_PORT;
import static com.linkedin.venice.ConfigKeys.LISTENER_SSL_PORT;
import static com.linkedin.venice.ConfigKeys.ZOOKEEPER_ADDRESS;
import static org.testng.Assert.assertEquals;

import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.utils.PropertyBuilder;
import com.linkedin.venice.utils.VeniceProperties;
import java.util.Map;
import java.util.TreeMap;
import org.testng.annotations.Test;


public class TestVeniceRouterConfig {
  @Test
  public void basicConstruction() {
    VeniceProperties props = getPropertyBuilderWithBasicConfigsFilledIn().build();
    VeniceRouterConfig routerConfig = new VeniceRouterConfig(props);
    Map<String, String> clusterToD2Map = routerConfig.getClusterToD2Map();
    assertEquals(clusterToD2Map.size(), 1);
    assertEquals(clusterToD2Map.get("blah"), "blahD2");
  }

  private PropertyBuilder getPropertyBuilderWithBasicConfigsFilledIn() {
    return new PropertyBuilder().put(CLUSTER_NAME, "blah")
        .put(LISTENER_PORT, 1)
        .put(LISTENER_SSL_PORT, 2)
        .put(ZOOKEEPER_ADDRESS, "host:1234")
        .put(KAFKA_BOOTSTRAP_SERVERS, "host:2345")
        .put(CLUSTER_TO_D2, "blah:blahD2");
  }

  @Test
  public void testParseRetryThresholdForBatchGet() {
    String retryThresholdConfig = "1-10:20,11-50:50,51-200:80,201-:1000";
    TreeMap<Integer, Integer> retryThresholdMap =
        VeniceRouterConfig.parseRetryThresholdForBatchGet(retryThresholdConfig);
    assertEquals((int) retryThresholdMap.get(1), 20);
    assertEquals((int) retryThresholdMap.get(11), 50);
    assertEquals((int) retryThresholdMap.get(51), 80);
    assertEquals((int) retryThresholdMap.get(201), 1000);

    assertEquals((int) retryThresholdMap.floorEntry(1).getValue(), 20);
    assertEquals((int) retryThresholdMap.floorEntry(30).getValue(), 50);
    assertEquals((int) retryThresholdMap.floorEntry(500).getValue(), 1000);

    // Config with un-ordered range
    String unorderedRetryThresholdConfig = "51-200:80,11-50:50,201-:1000,1-10:20";
    retryThresholdMap = VeniceRouterConfig.parseRetryThresholdForBatchGet(unorderedRetryThresholdConfig);
    assertEquals((int) retryThresholdMap.get(1), 20);
    assertEquals((int) retryThresholdMap.get(11), 50);
    assertEquals((int) retryThresholdMap.get(51), 80);
    assertEquals((int) retryThresholdMap.get(201), 1000);
  }

  @Test(expectedExceptions = VeniceException.class)
  public void testParseRetryThresholdForBatchGetWithKeyRangeGap() {
    String retryThresholdConfig = "1-10:20,51-200:80,201-:1000";
    VeniceRouterConfig.parseRetryThresholdForBatchGet(retryThresholdConfig);
  }

  @Test(expectedExceptions = VeniceException.class)
  public void testParseRetryThresholdForBatchGetWithWithInvalidFormat() {
    String retryThresholdConfig = "1-10:20,11-50:50,51-:80,201-:1000";
    VeniceRouterConfig.parseRetryThresholdForBatchGet(retryThresholdConfig);
  }

  @Test(expectedExceptions = VeniceException.class)
  public void testParseRetryThresholdForBatchGetWithInvalidSeparator() {
    String retryThresholdConfig = "1-10:20,11-50:50,51-200::80,201-:1000";
    VeniceRouterConfig.parseRetryThresholdForBatchGet(retryThresholdConfig);
  }

  @Test(expectedExceptions = VeniceException.class)
  public void testParseRetryThresholdForBatchGetWithoutStartingFrom1() {
    String retryThresholdConfig = "2-10:20,11-50:50,51-200::80,201-:1000";
    VeniceRouterConfig.parseRetryThresholdForBatchGet(retryThresholdConfig);
  }

  @Test(expectedExceptions = VeniceException.class)
  public void testParseRetryThresholdForBatchGetWithoutUnlimitedKeyCount() {
    String retryThresholdConfig = "2-10:20,11-50:50,51-200::80,201-500:1000";
    VeniceRouterConfig.parseRetryThresholdForBatchGet(retryThresholdConfig);
  }
}
