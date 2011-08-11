/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.i18n.phonenumbers.tools;

import com.google.i18n.phonenumbers.geocoding.AreaCodeMap;
import com.google.i18n.phonenumbers.geocoding.MappingFileProvider;
import com.google.i18n.phonenumbers.tools.GenerateAreaCodeData.AreaCodeMappingHandler;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Unittests for GenerateAreaCodeData.java
 *
 * @author Philippe Liard
 */
public class GenerateAreaCodeDataTest extends TestCase {
  private static final SortedMap<Integer, Set<String>> AVAILABLE_DATA_FILES;
  static {
    SortedMap<Integer, Set<String>> temporaryMap = new TreeMap<Integer, Set<String>>();

    // Languages for US.
    GenerateAreaCodeData.addConfigurationMapping(temporaryMap, new File("1_en"));
    GenerateAreaCodeData.addConfigurationMapping(temporaryMap, new File("1_en_US"));
    GenerateAreaCodeData.addConfigurationMapping(temporaryMap, new File("1_es"));

    // Languages for France.
    GenerateAreaCodeData.addConfigurationMapping(temporaryMap, new File("33_fr"));
    GenerateAreaCodeData.addConfigurationMapping(temporaryMap, new File("33_en"));

    // Languages for China.
    GenerateAreaCodeData.addConfigurationMapping(temporaryMap, new File("86_zh_Hans"));

    AVAILABLE_DATA_FILES = Collections.unmodifiableSortedMap(temporaryMap);
  }

  public void testAddConfigurationMapping() {
    assertEquals(3, AVAILABLE_DATA_FILES.size());

    Set<String> languagesForUS = AVAILABLE_DATA_FILES.get(1);
    assertEquals(3, languagesForUS.size());
    assertTrue(languagesForUS.contains("en"));
    assertTrue(languagesForUS.contains("en_US"));
    assertTrue(languagesForUS.contains("es"));

    Set<String> languagesForFR = AVAILABLE_DATA_FILES.get(33);
    assertEquals(2, languagesForFR.size());
    assertTrue(languagesForFR.contains("fr"));
    assertTrue(languagesForFR.contains("en"));

    Set<String> languagesForCN = AVAILABLE_DATA_FILES.get(86);
    assertEquals(1, languagesForCN.size());
    assertTrue(languagesForCN.contains("zh_Hans"));
  }

  public void testOutputBinaryConfiguration() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    GenerateAreaCodeData.outputBinaryConfiguration(AVAILABLE_DATA_FILES, byteArrayOutputStream);
    MappingFileProvider mappingFileProvider = new MappingFileProvider();
    mappingFileProvider.readExternal(
        new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray())));
    assertEquals("1|en,en_US,es,\n33|en,fr,\n86|zh_Hans,\n", mappingFileProvider.toString());
  }

  private static Map<Integer, String> parseTextFileHelper(String input) throws IOException {
    final Map<Integer, String> mappings = new HashMap<Integer, String>();
    GenerateAreaCodeData.parseTextFile(new ByteArrayInputStream(input.getBytes()),
                                       new AreaCodeMappingHandler() {
      @Override
      public void process(int areaCode, String location) {
        mappings.put(areaCode, location);
      }
    });
    return mappings;
  }

  public void testParseTextFile() throws IOException {
    Map<Integer, String> result = parseTextFileHelper("331|Paris\n334|Marseilles\n");
    assertEquals(2, result.size());
    assertEquals("Paris", result.get(331));
    assertEquals("Marseilles", result.get(334));
  }

  public void testParseTextFileIgnoresComments() throws IOException {
    Map<Integer, String> result = parseTextFileHelper("# Hello\n331|Paris\n334|Marseilles\n");
    assertEquals(2, result.size());
    assertEquals("Paris", result.get(331));
    assertEquals("Marseilles", result.get(334));
  }

  public void testParseTextFileIgnoresBlankLines() throws IOException {
    Map<Integer, String> result = parseTextFileHelper("\n331|Paris\n334|Marseilles\n");
    assertEquals(2, result.size());
    assertEquals("Paris", result.get(331));
    assertEquals("Marseilles", result.get(334));
  }

  public void testParseTextFileIgnoresTrailingWhitespaces() throws IOException {
    Map<Integer, String> result = parseTextFileHelper("331|Paris  \n334|Marseilles  \n");
    assertEquals(2, result.size());
    assertEquals("Paris", result.get(331));
    assertEquals("Marseilles", result.get(334));
  }

  public void testParseTextFileThrowsExceptionWithMalformattedData() throws IOException {
    try {
      parseTextFileHelper("331");
      fail();
    } catch (RuntimeException e) {
      // Expected.
    }
  }

  public void testParseTextFileThrowsExceptionWithMissingLocation() throws IOException {
    try {
      parseTextFileHelper("331|");
      fail();
    } catch (RuntimeException e) {
      // Expected.
    }
  }

  public void testSplitMap() {
    SortedMap<Integer, String> mappings = new TreeMap<Integer, String>();
    List<File> outputFiles = Arrays.asList(new File("1201_en"), new File("1202_en"));
    mappings.put(12011, "Location1");
    mappings.put(12012, "Location2");
    mappings.put(12021, "Location3");
    mappings.put(12022, "Location4");

    Map<File, SortedMap<Integer, String>> splitMaps =
        GenerateAreaCodeData.splitMap(mappings, outputFiles);
    assertEquals(2, splitMaps.size());
    assertEquals("Location1", splitMaps.get(new File("1201_en")).get(12011));
    assertEquals("Location2", splitMaps.get(new File("1201_en")).get(12012));
    assertEquals("Location3", splitMaps.get(new File("1202_en")).get(12021));
    assertEquals("Location4", splitMaps.get(new File("1202_en")).get(12022));
  }

  private static String convertDataHelper(String input) throws IOException {
    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(input.getBytes());
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    SortedMap<Integer, String> areaCodeMappings =
        GenerateAreaCodeData.readMappingsFromTextFile(byteArrayInputStream);
    GenerateAreaCodeData.writeToBinaryFile(areaCodeMappings, byteArrayOutputStream);
    // The byte array output stream now contains the corresponding serialized area code map. Try
    // to deserialize it and compare it with the initial input.
    AreaCodeMap areaCodeMap = new AreaCodeMap();
    areaCodeMap.readExternal(
        new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray())));

    return areaCodeMap.toString();
  }

  public void testConvertData() throws IOException {
    String input = "331|Paris\n334|Marseilles\n";

    String dataAfterDeserialization = convertDataHelper(input);
    assertEquals(input, dataAfterDeserialization);
  }

  public void testConvertDataThrowsExceptionWithDuplicatedAreaCodes() throws IOException {
    String input = "331|Paris\n331|Marseilles\n";

    try {
      convertDataHelper(input);
      fail();
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
