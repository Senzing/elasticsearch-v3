package com.senzing.g2.elasticsearch;

import java.io.StringReader;
import java.util.List;
import java.util.LinkedList;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

public class G2EntityData 
{
	// This class represents a minimal data representation for a resolved entity in 
	// the search system.  It contains only the entity ID, and the JSON records that
	// are contained in that resolved entity.  (We keep the data at a minimal here,
	// so that we don't get other data showing up in search results.)
	
	private String m_resolvedEntityJsonData;
	private Long m_resolvedEntityID;
	
	public G2EntityData(String resolvedEntityDataString)
	{
		// This function parses a resolved entity document, and retrieves the necessary data.
		StringReader sr = new StringReader(resolvedEntityDataString);
		JsonReader jsonReader  = Json.createReader(sr);
		JsonObject jsonObject  = jsonReader.readObject();
		JsonObject resEntObject = jsonObject.getJsonObject("RESOLVED_ENTITY");
		
		// get the entity ID
		JsonNumber resEntIDNum = resEntObject.getJsonNumber("ENTITY_ID");
		m_resolvedEntityID = resEntIDNum.longValue();
		
		// create a document writer for creating the document to be indexed
		JsonObjectBuilder outputBuilder = Json.createObjectBuilder();
		
		// get the json data from the individual records within the resolved entity.
		JsonArrayBuilder jsonDataArrayBuilder = Json.createArrayBuilder();
		JsonArray recordsArray = resEntObject.getJsonArray("RECORDS");
		int numRecordsInArray = recordsArray.size();
		List<G2RecordInfo> recordInfoList = new LinkedList<G2RecordInfo>();
		for (int i = 0; i < numRecordsInArray; i++)
		{
			JsonObject recordObject = recordsArray.getJsonObject(i);
			JsonObject recordJsonDataObject = recordObject.getJsonObject("JSON_DATA");
			jsonDataArrayBuilder.add(i,recordJsonDataObject);
			
			JsonString dataSourceDataObject = recordObject.getJsonString("DATA_SOURCE");
			JsonString recordIDDataObject = recordObject.getJsonString("RECORD_ID");
			recordInfoList.add(new G2RecordInfo(dataSourceDataObject.getString(),recordIDDataObject.getString()));
		}
		outputBuilder.add("JSON_DATA",jsonDataArrayBuilder);
		JsonArrayBuilder recordArrayBuilder = Json.createArrayBuilder();
		for (G2RecordInfo recordInfo : recordInfoList)
		{
			JsonObjectBuilder recordInfoBuilder = Json.createObjectBuilder();
			recordInfoBuilder.add("DATA_SOURCE",recordInfo.getDataSource());
			recordInfoBuilder.add("RECORD_ID",recordInfo.getRecordID());
			recordArrayBuilder.add(recordInfoBuilder);
		}
		outputBuilder.add("RECORDS",recordArrayBuilder);
		
		// store the indexable document
		m_resolvedEntityJsonData = JsonStringifier.stringifyJson(outputBuilder.build()).toString();
	}

	public String getRecordData()
	{
		return m_resolvedEntityJsonData;
	}
	
	public Long getEntityID()
	{
		return m_resolvedEntityID;
	}
	
	public String getElasticSearchEntityIdentifier()
	{
		return ("{\"ENTITY_ID\":\""+m_resolvedEntityID+"\"}");
	}
}
