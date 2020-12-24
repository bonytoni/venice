package com.linkedin.davinci.helix;

import com.linkedin.davinci.kafka.consumer.StoreIngestionService;
import com.linkedin.venice.helix.HelixPartitionStatusAccessor;
import com.linkedin.venice.meta.ReadOnlyStoreRepository;
import com.linkedin.davinci.config.VeniceConfigLoader;
import com.linkedin.davinci.storage.StorageService;
import com.linkedin.venice.utils.HelixUtils;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.apache.helix.participant.statemachine.StateModel;


/**
 * State Transition Handler factory for handling Leader/Follower resources in the storage node.
 * For Online/Offline resources, please refer to {@link VeniceStateModelFactory}
 */

public class LeaderFollowerParticipantModelFactory extends AbstractParticipantModelFactory {
  private LeaderFollowerStateModelNotifier leaderFollowerStateModelNotifier = new LeaderFollowerStateModelNotifier();

  public LeaderFollowerParticipantModelFactory(StoreIngestionService storeIngestionService,
      StorageService storageService, VeniceConfigLoader configService, ExecutorService executorService,
      ReadOnlyStoreRepository metadataRepo,
      Optional<CompletableFuture<HelixPartitionStatusAccessor>> partitionPushStatusAccessorFuture,
      String instanceName) {
    super(storeIngestionService, storageService, configService, executorService, metadataRepo,
        partitionPushStatusAccessorFuture, instanceName);

    // Add a new notifier to let state model knows ingestion has caught up the lag so that it can complete the offline to
    // standby state transition.
    storeIngestionService.addLeaderFollowerModelNotifier(leaderFollowerStateModelNotifier);
    logger.info("LeaderFollowerParticipantModelFactory Created");
  }

  @Override
  public StateModel createNewStateModel(String resourceName, String partitionName) {
    logger.info("Creating LeaderFollowerParticipantModel handler for partition: " + partitionName);
    return new LeaderFollowerParticipantModel(getStoreIngestionService(), getStorageService(),
        getConfigService().getStoreConfig(HelixUtils.getResourceName(partitionName)),
        HelixUtils.getPartitionId(partitionName), leaderFollowerStateModelNotifier, getMetadataRepo(),
        partitionPushStatusAccessorFuture, instanceName);
  }
}