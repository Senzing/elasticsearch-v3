package com.senzing.g2.elasticsearch;

import java.io.StringReader;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

// This class will search all of the attributes of a JSON document, and return a list of fields that 
// start with a particular substring.

public class JsonFieldValueFinder 
{
	public static void findValues(String sourceJson, String searchValue, Set<String> searchResults)
	{
		StringReader sr = new StringReader(sourceJson);
		JsonReader jsonReader  = Json.createReader(sr);
		JsonObject jsonObject  = jsonReader.readObject();
		findValues(jsonObject,searchValue,searchResults);
	}
	
	public static void findValues(JsonObject sourceJson, String searchValue, Set<String> searchResults)
	{
		String preparedSearchString = standardizeForMatching(searchValue);
		searchResults.clear();
		
		sourceJson.entrySet().stream().forEach((entry) -> {
	           //String key = entry.getKey();
	           JsonValue value = entry.getValue();
	           searchForValue(value,preparedSearchString,searchResults);
	         });
	}
	
	private static void searchForValue(JsonValue sourceValue, String searchValue, Set<String> searchResults)
	{
		ValueType valueType = sourceValue.getValueType();
		switch (valueType)
		{
		case ARRAY:
			JsonArray sourceArray = sourceValue.asJsonArray();
			sourceArray.stream().forEach((entry) -> {
				searchForValue(entry,searchValue,searchResults);
		         });
			break;
		case OBJECT:
			JsonObject sourceObject = sourceValue.asJsonObject();
			sourceObject.entrySet().stream().forEach((entry) -> {
	           JsonValue value = entry.getValue();
	           searchForValue(value,searchValue,searchResults);
	         });
			break;
		default:
			{
				String rawValue = Utils.getSimpleRawValue(sourceValue);
				if (standardizeForMatching(rawValue).startsWith(searchValue))
				{
					searchResults.add(rawValue);
				}
			}
		}
	}

	private static String standardizeForMatching(String str)
	{
		return str.toLowerCase();
	}
}
