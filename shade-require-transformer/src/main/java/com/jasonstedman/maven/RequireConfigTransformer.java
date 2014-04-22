
package com.jasonstedman.maven;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;

/* Copyright 2014 Jason Stedman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This ResourceTransformer implementation merges RequireJS configurations
 * from multiple war projects into a unified data-main script. This enables
 * modular JavaScript development using Maven for dependency management
 * without imposing architectural choices on the user.
 * 
 */

public class RequireConfigTransformer implements ResourceTransformer
{

	public String dataMainPath;
	public String configFilePattern;
	public String initialDefinition;
	
	private Pattern configFilePatternInstance;
	private ArrayList<Map<String, Object>> configBlocks = new ArrayList<Map<String, Object>>();
	private String initBlock;
	private boolean hasTransformedResource = false;

	private static ObjectMapper mapper = new ObjectMapper();
	private static Logger logger;
	
	
	static{
		mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
		logger = Logger.getLogger("RequireConfigTransformer");
	}
	
	public boolean canTransformResource(String resource) {
		configFilePatternInstance = Pattern.compile(configFilePattern);
		if(resource.equals(initialDefinition)){
			logger.fine(resource + " matches " + initialDefinition);
			return true;
		}
		if(configFilePatternInstance.matcher(resource).matches()){
			logger.fine(resource + " matches " + configFilePattern);
			return true;
		}
		else return false;
	}

	@SuppressWarnings("unchecked")
	public void processResource(String resource, InputStream is,
			List<Relocator> relocators) throws IOException {
		String block = IOUtils.toString(is, "utf-8");
		if(resource.equals(initialDefinition)){
			initBlock = block;
		} else if(configFilePatternInstance.matcher(resource).matches()){
			block = block.replaceFirst("\\s*require\\s*.\\s*config\\s*\\(", "");
			block = block.substring(0, block.lastIndexOf(')'));
			logger.finer(block);
			configBlocks.add(mapper.readValue(block, Map.class));
		}
		hasTransformedResource = true;
	}
	
	public boolean hasTransformedResource() {
		return hasTransformedResource ;
	}

	public void modifyOutputStream(JarOutputStream jos) throws IOException {
		logger.info("Creating merged data-main script at war path : " + dataMainPath);
		logger.info("Merging require configs matching path pattern : " + configFilePattern);
		logger.info("Using initial define block from : " + initialDefinition);
		
		Map<String, Object> configBlock = mergeConfigBlocks();
		
		jos.putNextEntry( new JarEntry( dataMainPath ) );
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(jos));
		writer.write("require.config(");
		writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configBlock));
		writer.write(");");
		writer.newLine();
		writer.write(initBlock);
		writer.flush();
	}

	private Map<String, Object> mergeConfigBlocks() {
		HashMap<String, Object> configBlock = new HashMap<String, Object>();
		for(Map<String, Object> entry : configBlocks){
			mergeMaps(configBlock, entry);
		}
		return configBlock;
	}

	@SuppressWarnings("unchecked")
	private void mergeMaps(Map<String, Object> a, Map<String, Object> b) {
		for(String key : b.keySet()){
			Object value = b.get(key);
			if(value instanceof String){
				if(a.containsKey(key) && ! a.get(key).equals(value)){
					throw new RuntimeException("duplicate config for key : " + key);
				} else {
					a.put(key, value);
				}
			} else if(value instanceof Map){
				if(a.containsKey(key)){
					mergeMaps((Map<String, Object>)a.get(key), (Map<String, Object>)value);
				} else {
					a.put(key, value);
				}
			}
		}
	}

}
