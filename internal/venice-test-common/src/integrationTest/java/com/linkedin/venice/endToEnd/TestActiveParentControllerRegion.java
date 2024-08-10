package com.linkedin.venice.endToEnd;

import static com.linkedin.venice.controller.ParentControllerRegionState.*;

import com.linkedin.venice.controllerapi.ControllerClient;
import com.linkedin.venice.controllerapi.NewStoreResponse;
import com.linkedin.venice.controllerapi.StoreResponse;
import com.linkedin.venice.controllerapi.VersionCreationResponse;
import com.linkedin.venice.integration.utils.ServiceFactory;
import com.linkedin.venice.integration.utils.VeniceControllerWrapper;
import com.linkedin.venice.integration.utils.VeniceMultiClusterWrapper;
import com.linkedin.venice.integration.utils.VeniceMultiRegionClusterCreateOptions;
import com.linkedin.venice.integration.utils.VeniceTwoLayerMultiRegionMultiClusterWrapper;
import com.linkedin.venice.utils.TestUtils;
import com.linkedin.venice.utils.Utils;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class TestActiveParentControllerRegion {
  private static final Logger LOGGER = LogManager.getLogger(TestActiveParentControllerRegion.class);

  private VeniceTwoLayerMultiRegionMultiClusterWrapper venice;

  @BeforeClass
  public void setUp() {
    venice = ServiceFactory.getVeniceTwoLayerMultiRegionMultiClusterWrapper(
        new VeniceMultiRegionClusterCreateOptions.Builder().numberOfRegions(2)
            .numberOfClusters(1)
            .numberOfChildControllers(1)
            .numberOfServers(1)
            .numberOfRouters(1)
            .replicationFactor(1)
            .parentControllerInChildRegion(true)
            .build());
  }

  @AfterClass
  public void cleanUp() {
    Utils.closeQuietlyWithErrorLogged(venice);
  }

  /**
   * 1. Create two child regions with one ACTIVE parent controller in one region and one PASSIVE parent controller in the other region.
   * 2. Perform store operation and VPJ job to both parent controllers.
   * 3. Verify ACTIVE parent controller successfully completes operations and PASSIVE parent controller throws exception.
   * 4. Verify child controllers are updated.
   *
   * TODO now: create 2 zk in child region: 1 for parent controller and 1 for child controller
   *
   * TODO after:
   * 5. To imitate a region going down, we switch ACTIVE and PASSIVE states for both parent controllers
   * 6. Migrate metadata from old ACTIVE parent controller to the new ACTIVE parent controller
   * 7. Verify both parent controllers are synced.
   * 8. Perform new operation(i.e., createStore) to both parent controllers.
   * 9. Verify new ACTIVE parent controller successfully completes operation(getStore) and new PASSIVE parent controller throws exception.
   * 10. Verify child controllers are updated
   */
  @Test
  public void testActiveParentControllerRegion() {
    List<VeniceControllerWrapper> parentControllers = venice.getParentControllers();
    List<VeniceMultiClusterWrapper> childDatacenters = venice.getChildRegions();
    String clusterName = venice.getClusterNames()[0];
    Assert.assertEquals(parentControllers.size(), 2);
    Assert.assertEquals(childDatacenters.size(), 2);
    Assert.assertEquals(venice.getClusterNames().length, 1);
    Assert.assertEquals(childDatacenters.get(0).getClusters().size(), 1);
    VeniceControllerWrapper parentController0 = parentControllers.get(0);
    VeniceControllerWrapper parentController1 = parentControllers.get(1);
    for (VeniceControllerWrapper parentController: parentControllers) {
      LOGGER.info(
          "Parent controller {} is in region {}",
          parentController.getVeniceAdmin().getParentControllerRegionState(),
          parentController.getVeniceAdmin().getRegionName());
    }
    Assert.assertTrue(parentController0.getVeniceAdmin().isParent());
    Assert.assertTrue(parentController1.getVeniceAdmin().isParent());
    Assert.assertEquals(parentController0.getVeniceAdmin().getParentControllerRegionState(), ACTIVE);
    Assert.assertEquals(parentController1.getVeniceAdmin().getParentControllerRegionState(), PASSIVE);

    String parentControllerURL0 = parentController0.getControllerUrl();
    String parentControllerURL1 = parentController1.getControllerUrl();
    try (ControllerClient parentControllerClient0 = new ControllerClient(clusterName, parentControllerURL0);
        ControllerClient parentControllerClient1 = new ControllerClient(clusterName, parentControllerURL1);
        ControllerClient dc0Client =
            new ControllerClient(clusterName, childDatacenters.get(0).getControllerConnectString());
        ControllerClient dc1Client =
            new ControllerClient(clusterName, childDatacenters.get(1).getControllerConnectString())) {
      LOGGER.info("Parent0 call leader controller " + parentControllerClient0.getLeaderControllerUrl());
      try {
        LOGGER.info("Parent1 call leader controller " + parentControllerClient1.getLeaderControllerUrl());
        Assert.fail("Parent1 should not be able to call leader controller");
      } catch (Exception e) {
        LOGGER.info("Parent1 call leader controller failed as expected");
      }
      LOGGER.info("dc0 call leader controller " + dc0Client.getLeaderControllerUrl());
      LOGGER.info("dc1 call leader controller" + dc1Client.getLeaderControllerUrl());

      // steps 2 + 3
      String storeName = "test-store";
      String keySchemaStr = "\"string\"";
      String valueSchemaStr = "\"string\"";

      LOGGER.info("Creating new store in parent controller 0");
      NewStoreResponse parentController0NewStoreResponse =
          parentControllerClient0.createNewStore(storeName, "", keySchemaStr, valueSchemaStr);
      LOGGER.info("parent controller 0 new store response: " + parentController0NewStoreResponse);
      Assert.assertFalse(parentController0NewStoreResponse.isError());

      LOGGER.info("Creating new store in parent controller 1");
      NewStoreResponse parentController1NewStoreResponse =
          parentControllerClient1.createNewStore(storeName, "", keySchemaStr, valueSchemaStr);
      LOGGER.info("parent controller 0 new store response: " + parentController0NewStoreResponse);
      Assert.assertTrue(parentController1NewStoreResponse.isError());

      LOGGER.info("empty push to parent controller 0");
      VersionCreationResponse parentController0VersionCreationResponse =
          parentControllerClient0.emptyPush(storeName, "test", 1L);
      LOGGER.info("parent controller 0 version creation response: " + parentController0VersionCreationResponse);
      Assert.assertFalse(parentController0VersionCreationResponse.isError());
      Assert.assertEquals(parentController0VersionCreationResponse.getVersion(), 1);

      VersionCreationResponse parentController1VersionCreationResponse =
          parentControllerClient1.emptyPush(storeName, "test", 1L);
      Assert.assertTrue(parentController1VersionCreationResponse.isError());

      // step 4
      TestUtils.waitForNonDeterministicAssertion(10, TimeUnit.SECONDS, false, true, () -> {
        StoreResponse storeResponse = dc0Client.getStore(storeName);
        Assert.assertFalse(storeResponse.isError());
        Assert.assertEquals(storeResponse.getStore().getCurrentVersion(), 1);

      });
      TestUtils.waitForNonDeterministicAssertion(10, TimeUnit.SECONDS, false, true, () -> {
        StoreResponse storeResponse = dc1Client.getStore(storeName);
        Assert.assertFalse(storeResponse.isError());
        Assert.assertEquals(storeResponse.getStore().getCurrentVersion(), 1);
      });
    }
  }
}