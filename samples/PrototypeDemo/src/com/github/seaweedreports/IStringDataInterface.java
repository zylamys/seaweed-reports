package com.github.seaweedreports;

import java.util.*;

/**
 * Interface to result set
 * 
 */
public interface IStringDataInterface {
    public abstract LinkedHashMap<Integer, LinkedList<String>> getData(String detailTable, String masterTable, LinkedList<String> fieldList, String conditions);
}
