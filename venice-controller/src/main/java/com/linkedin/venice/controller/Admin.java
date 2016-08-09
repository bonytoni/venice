package com.linkedin.venice.controller;

import com.linkedin.venice.helix.Replica;
import com.linkedin.venice.job.ExecutionStatus;
import com.linkedin.venice.kafka.TopicManager;
import com.linkedin.venice.meta.Instance;
import com.linkedin.venice.meta.Store;
import com.linkedin.venice.meta.Version;
import com.linkedin.venice.schema.SchemaEntry;

import java.util.Collection;
import java.util.List;


/**
 * Created by athirupa on 2/1/16.
 */
public interface Admin {
    void start(String clusterName);

    void addStore(String clusterName, String storeName, String owner);

    Version addVersion(String clusterName, String storeName, int versionNumber, int numberOfPartition,
        int replicaFactor);

    Version incrementVersion(String clusterName, String storeName, int numberOfPartition, int replicaFactor);

    int getCurrentVersion(String clusterName, String storeName);

    Version peekNextVersion(String clusterName, String storeName);

    List<Version> versionsForStore(String clusterName, String storeName);

    List<Store> getAllStores(String clusterName);

    void reserveVersion(String clusterName, String storeName, int versionNumberToReserve);

    void setCurrentVersion(String clusterName, String storeName, int versionNumber);

    void startOfflinePush(String clusterName, String kafkaTopic, int numberOfPartition, int replicaFactor);

    void deleteOldStoreVersion(String clusterName, String kafkaTopic);

    SchemaEntry getKeySchema(String clusterName, String storeName);

    SchemaEntry initKeySchema(String clusterName, String storeName, String keySchemaStr);

    Collection<SchemaEntry> getValueSchemas(String clusterName, String storeName);

    int getValueSchemaId(String clusterName, String storeName, String valueSchemaStr);

    SchemaEntry getValueSchema(String clusterName, String storeName, int id);

    SchemaEntry addValueSchema(String clusterName, String storeName, String valueSchemaStr);

    void stop(String clusterName);

    /**
     * Query the status of the offline job by given kafka topic.
     * TODO We use kafka topic to tracking the status now but in the further we should use jobId instead of kafka
     * TODO topic. Right now each kafka topic only have one offline job. But in the further one kafka topic could be
     * TODO assigned multiple jobs like data migration job etc.
     * @param clusterName
     * @param kafkaTopic
     * @return the map of job Id to job status.
     */
    ExecutionStatus getOffLineJobStatus(String clusterName, String kafkaTopic);

    /**
     * TODO : Currently bootstrap servers are common per Venice Controller cluster
     * This needs to be configured at per store level or per version level.
     * The Kafka bootstrap servers should also be dynamically sent to the Storage Nodes
     * and only controllers should be aware of them.
     *
     * @return kafka bootstrap servers url, if there are multiple will be comma separated.
     */
    String getKafkaBootstrapServers();

    TopicManager getTopicManager();

    /**
     * Check if this controller itself is the master controller of given cluster or not.
     * @param clusterName
     * @return
     */
    boolean isMasterController(String clusterName);

    /**
    * Calculate how many partitions are needed for the given store and size.
    * @param storeName
    * @param storeSize
    * @return
    */
    int calculateNumberOfPartitions(String clusterName, String storeName, long storeSize);

    int getReplicaFactor(String clusterName, String storeName);

    /**
    * Get all of replicas which are bootstrapping from given kafka topic.
    */
    List<Replica> getBootstrapReplicas(String clusterName, String kafkaTopic);

    /**
    * Get all of replicas which are in the error status of given kafka topic.
    */
    List<Replica> getErrorReplicas(String clusterName, String kafkaTopic);

    /**
     * Is the given instance able to remove out from given cluster. For example, if there is only one replica alive in this
     * cluster which is hosted on given instance. This instance should not be removed out of cluster, otherwise Venice will
     * lose data.
     *
     * @param instanceId nodeId of helix participant. HOST_PORT.
     */
    boolean isInstanceRemovable(String clusterName, String instanceId);

    /**
     * Get instance of master controller. If there is no master controller for the given cluster, throw a
     * VeniceException.
     */
    Instance getMasterController(String clusterName);

    void close();
}
