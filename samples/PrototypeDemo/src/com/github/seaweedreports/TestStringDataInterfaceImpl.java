package com.github.seaweedreports;

import java.util.*;

/**
 * Get data from the database
 */
public class TestStringDataInterfaceImpl implements IStringDataInterface {

    Integer ic = 0;

    public LinkedHashMap<Integer, LinkedList<String>> getData(String tableName, String parentTableName, LinkedList<String> fieldNames, String filter) {
        LinkedHashMap<Integer, LinkedList<String>> dataset = new LinkedHashMap<Integer, LinkedList<String>>();
        System.out.println();
        System.out.println("select from " + tableName + " - " + parentTableName + " (" + fieldNames + ")");
        if (fieldNames == null) {
            return null;
        }
        for (int i = 1; i < (tableName.equals("employer") ? 3 : 0); i++) {
            LinkedList<String> list = new LinkedList<String>();
            for (String s : fieldNames) {
                list.add("***" + tableName + "." + s + " (" + parentTableName + "=" + filter + ")" + ic + "***");
                ic++;
            }
            // Just put the list of string values to create record
            dataset.put(i, list);
        }

        return dataset;
    }

}
