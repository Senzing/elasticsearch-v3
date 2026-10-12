package com.senzing.g2.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.util.BinaryData;
import co.elastic.clients.util.ContentType;

import com.senzing.g2.engine.G2Engine;
import com.senzing.g2.engine.G2JNI;
import com.senzing.g2.engine.Result;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class G2toElastic {
  public static void main(String[] args) {
    // define ElasticSearch index information

    String hostName = System.getenv("ELASTIC_HOSTNAME");
    String elasticSearchHostname = (hostName != null) ? hostName : "localhost"; // The hostname for the elasticsearch
                                                                                // instance

    String portNum = System.getenv("ELASTIC_PORT");
    int elasticSearchPortNumber = (portNum != null) ? Integer.parseInt(portNum) : 9200;// the exposed port for
                                                                                       // elasticsearch

    String indexName = System.getenv("ELASTIC_INDEX_NAME");
    String elasticSearchIndexName = (indexName != null) ? indexName : "g2index"; // This value can be whatever you want,
                                                                                 // adhering to elasticsearch's index
                                                                                 // syntax

    System.out.println("****Program started****");
    System.out.println("Initializing G2");

    // ****************************Creating G2Engine instance********************
    // define G2 connecting information
    String moduleName = "G2ElasticSearch";
    boolean verboseLogging = false;
    String SENZING_ENGINE_CONFIGURATION_JSON = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
    if (SENZING_ENGINE_CONFIGURATION_JSON == null) {
      System.out.println(
          "The environment variable SENZING_ENGINE_CONFIGURATION_JSON must be set with a proper JSON configuration.");
      System.out.println(
          "Please see https://senzing.zendesk.com/hc/en-us/articles/360038774134-G2Engine-Configuration-and-the-Senzing-SDK");
      System.exit(1);
    }

    // Connect to the G2 Engine
    System.out.println("Connecting to G2 engine.");
    G2JNI g2Engine = new G2JNI();
    int returnValue = g2Engine.init(moduleName, SENZING_ENGINE_CONFIGURATION_JSON, verboseLogging);
    if (returnValue != 0) {
      printG2Error(g2Engine, "Could not connect to G2", returnValue);
      System.out.println("****Program failed****");
      System.exit(1);
    }

    boolean success = false;
    try {
      // ****************************Creating elasticsearch objects********************
      System.out.println("Making elasticsearch clients");
      String elasticSearchUrl = "http://" + elasticSearchHostname + ":" + elasticSearchPortNumber;
      AtomicLong indexedCount = new AtomicLong();
      AtomicLong failedCount = new AtomicLong();

      try (ElasticsearchClient esClient = ElasticsearchClient.of(b -> b.host(elasticSearchUrl));
          BulkIngester<Void> ingester = BulkIngester.of(b -> b
              .client(esClient)
              .maxOperations(25) // This setting changes how many documents get sent at a times
              .flushInterval(250, TimeUnit.MILLISECONDS) // This setting changes how often the ingester gets flushed
              .listener(new BulkResultListener(indexedCount, failedCount)))) {

        long exportFlags = G2Engine.G2_ENTITY_INCLUDE_RECORD_JSON_DATA | G2Engine.G2_EXPORT_INCLUDE_ALL_ENTITIES;
        Result<Long> exportHandle = new Result<Long>();
        returnValue = g2Engine.exportJSONEntityReport(exportFlags, exportHandle);
        if (returnValue != 0) {
          printG2Error(g2Engine, "Could not export JSON report", returnValue);
        } else {
          System.out.println("Indexing entities");
          boolean exportComplete = false;
          try {
            StringBuffer entity = new StringBuffer();
            while (true) {
              entity.setLength(0);
              returnValue = g2Engine.fetchNext(exportHandle.getValue(), entity);
              if (returnValue != 0 || entity.length() == 0)
                break;
              G2EntityData entityData = new G2EntityData(entity.toString());
              BinaryData data = BinaryData.of(entityData.getRecordData().getBytes(StandardCharsets.UTF_8),
                  ContentType.APPLICATION_JSON);

              // This ingester does bulk indexes
              ingester.add(op -> op
                  .index(idx -> idx
                      .index(elasticSearchIndexName)
                      .document(data)));
            }
            if (returnValue != 0) {
              printG2Error(g2Engine, "Could not fetch the next entity", returnValue);
            } else {
              exportComplete = true;
            }
          } finally {
            g2Engine.closeExport(exportHandle.getValue());
          }
          success = exportComplete;
        }
      }
      System.out.println("Finished indexing: " + indexedCount.get() + " entities indexed, "
          + failedCount.get() + " failed");
      success = success && (failedCount.get() == 0);

    } catch (Exception e) {
      e.printStackTrace();
      success = false;
    } finally {
      // close the G2 engine instance
      System.out.println("Closing G2 engine interface.");
      returnValue = g2Engine.destroy();
      if (returnValue != 0) {
        printG2Error(g2Engine, "Could not disconnect from G2", returnValue);
        success = false;
      }
    }

    if (!success) {
      System.out.println("****Program failed****");
      System.exit(1);
    }
    System.out.println("****Program complete****");
    System.exit(0);
  }

  private static void printG2Error(G2JNI g2Engine, String message, int returnValue) {
    System.out.println(message);
    System.out.println("Return Code = " + returnValue);
    System.out.println("Exception Code = " + g2Engine.getLastExceptionCode());
    System.out.println("Exception = " + g2Engine.getLastException());
  }

  // Counts documents that were and weren't indexed so failures don't pass silently.
  private static class BulkResultListener implements BulkListener<Void> {
    private final AtomicLong indexedCount;
    private final AtomicLong failedCount;

    BulkResultListener(AtomicLong indexedCount, AtomicLong failedCount) {
      this.indexedCount = indexedCount;
      this.failedCount = failedCount;
    }

    @Override
    public void beforeBulk(long executionId, BulkRequest request, List<Void> contexts) {
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, List<Void> contexts, BulkResponse response) {
      for (BulkResponseItem item : response.items()) {
        if (item.error() != null) {
          failedCount.incrementAndGet();
          System.out.println("Failed to index document: " + item.error().reason());
        } else {
          indexedCount.incrementAndGet();
        }
      }
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, List<Void> contexts, Throwable failure) {
      failedCount.addAndGet(request.operations().size());
      System.out.println("Bulk request failed: " + failure);
    }
  }
}
