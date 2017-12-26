package com.github.seaweedreports;

import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import static org.w3c.dom.Node.ATTRIBUTE_NODE;
import static org.w3c.dom.Node.DOCUMENT_NODE;
import static org.w3c.dom.Node.ELEMENT_NODE;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Test XML report class
 */
public class TestMappedXmlReport {

    public static final String LIBRARY_VALUES = "lib:";
    private static final String QUOTE = "'";
    private static final int MAX_PATH_LEN = 20;
    
    // First table TODO eliminate this property
    public static final String startFrom = "firms";

    // Empty XML,
    // in this example it is filled with values  intentionally
    public static final String xmlDummy
            = "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n"
            + " <employer>\n"
            + "  <name>Seaweed inc.</name>\n"
            + "  <employees count=\"\">\n"            
            + "    <employee id=\"\">\n"
            + "    <!-- ====== Employee ====== -->\n"
            + "      <!--  Employee's name  -->\n"
            + "      <name>John</name>\n"
            + "      <!--  Employee's surname  -->\n"
            + "      <surname>Weed</surname>\n"
            + "      <age>18</age>\n"
            + "      <input_date>31.12.2011</input_date>\n"
            + "      <something></something>\n"
            + "    </employee>\n"
            + "    <summary>\n"
            + "      <averageAge>\n"
            + "      </averageAge>\n"            
            + "    </summary>\n"
            + "  </employees>\n"
            + "  <debt>18</debt>\n"
            + "</employer>\n";

    // Mapping between nodes and tables
    // All nodes are mandatory
    private String tableMapping
            = "employer=firms\n"
            + "employee=staff\n";

    // Mapping between nodes and fields
    // All nodes are mandatory
    public static final String fieldMapping
            = "employer/name=firm_name\n"
            + "id=emp_id\n"            
            + "count=empl_count\n"          
            + "employee/name=first_name\n"
            + "surname=family_name\n"
            + "employee/input_date=lib:sysdate\n"
            + "something=\n"
            + "averageAge=avg(age)\n"
            + "debt=lib:my_zero\n"
            + "age=age\n";

    private HashMap<String, String> tables;
    private LinkedList<String> tableRootNodes = null;

    LinkedHashMap<String, Node> lostTags = new LinkedHashMap<String, Node>();

    //--------------------- User functions ------------------------------
    /**
     * Empty string checking util
     */
    public static boolean isEmpty(final String s) {
        return s == null || "".equals(s.trim());
    }

    /**
     * Date formatting util
     */
    public static String formatDate(Date value, String format) {
        if (value == null || isEmpty(format)) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        String today = sdf.format(value);
        return today;
    }

    /**
     * TODO Implement constants and library functions here
     */
    public String getLibraryValues(String fieldName, IStringDataInterface dataAdapter, String filter) {
        if (fieldName.equals("lib:sysdate")) {
            return formatDate(new Date(), "dd.MM.yyyy");
        } else if (fieldName.equals("lib:my_zero")) {
            return "0.0000000000";
        } else if (fieldName.equals("lib:manager_types_list")) {
            System.out.println("================= Nested user query =================");
            LinkedHashMap<Integer, LinkedList<String>> dataset = dataAdapter.getData("managers", "employee", splitSemicolons("employee_type", ";"), filter);
            String orgType = "";
            for (Integer key : dataset.keySet()) {
                LinkedList<String> row = dataset.get(key);
                orgType = row.pop();
            }
            return "ERROR: unknown library value request: " + orgType;

        } else if (fieldName.equals("lib:manager_list")) {
            System.out.println("================= Nested user query =================");
            LinkedHashMap<Integer, LinkedList<String>> dataset = dataAdapter.getData("managers", "employee", splitSemicolons("first_name;family_name;position", ";"), filter);
            String managers = "";
            for (Integer key : dataset.keySet()) {
                LinkedList<String> row = dataset.get(key);
                managers = managers + row.pop() + " " + row.pop() + " " + row.pop() + "\n";
            }
            return managers;
        }

        LogFactory.getLog().error("calculateFields(): Unknown calculated field: " + fieldName);

        return "*ERROR IN LIBRARY VALUES*";
    }

    public String mapXmlElementToTable(String tagName) {
        String tableName = tables.get(tagName);
        if (tableName == null) {
            System.out.println("ERROR: Table is not mapped: " + tableName);
            return "";
        }
        return tableName;
    }

    public int countChildElements(NodeList foundNodes) {
        int counter = 0;
        for (int i = 0; i < foundNodes.getLength(); i++) {
            if (foundNodes.item(i).getNodeType() == ELEMENT_NODE) {
                counter++;
            }
        }
        return counter;
    }

    public int countChildNodes(NodeList foundNodes, NamedNodeMap foundAttr) {
        int counter = 0;
        for (int i = 0; i < foundNodes.getLength(); i++) {
            short type = foundNodes.item(i).getNodeType();

            if (type == ELEMENT_NODE) {
                counter++;
            }
        }
        if (foundAttr != null) {
            counter += foundAttr.getLength();
        }

        return counter;
    }

    public Node getElementByIndex(NodeList foundNodes, int index) {

        int counter = -1;
        for (int i = 0; i < foundNodes.getLength(); i++) {

            if (foundNodes.item(i).getNodeType() == ELEMENT_NODE) {
                counter++;
            }
            if (counter == index) {
                return foundNodes.item(i);
            }
        }
        return null;
    }

    public Node getNodeByIndex(NodeList foundNodes, NamedNodeMap foundAttr, int index) {
        int counter = -1;
        int numElements = foundNodes.getLength();
        if (foundAttr != null) {
            int numAttrs = foundAttr.getLength();
            if (index < numAttrs) {
                return (Node) foundAttr.item(index);
            } else {
                counter = numAttrs - 1;
            }
        }
        for (int i = 0; i < numElements; i++) {
            short type = foundNodes.item(i).getNodeType();
            if (type == ELEMENT_NODE) {
                ++counter;
            }
            if (counter == index) {
                return foundNodes.item(i);
            }
        }
        return null;
    }

    public Node getNodeByIndex(NodeList foundNodes, NamedNodeMap foundAttr, int index, int offset) {
        int counter = -1;
        int numElements = foundNodes.getLength();
        if (foundAttr != null) {
            int numAttrs = foundAttr.getLength();
            if (index < numAttrs) {
                return (Node) foundAttr.item(index);
            } else {
                counter = numAttrs - 1;
            }
        }
        System.err.println("Index search with offset");
        for (int i = 0; i < numElements; i++) {
            short type = foundNodes.item(i).getNodeType();
            System.err.println(foundNodes.item(i).getNodeName());
            if (type == ELEMENT_NODE) {
                ++counter;
            }
            if (counter == index + offset) {
                return foundNodes.item(i);
            }
        }
        return null;
    }

    public LinkedHashMap<String, String> parseMap(String strMap) {
        LinkedHashMap<String, String> mapXml = new LinkedHashMap<String, String>();
        String[] splitMap = strMap.split("\n");
        for (String s : splitMap) {
            String[] nameVal = s.split("=");
            if (mapXml.containsKey(nameVal[0])) {
                System.out.println("ERROR: Duplicate tag found in map: " + nameVal[0]);;
            }
            if (nameVal.length == 2) {
                mapXml.put(nameVal[0], nameVal[1]);
            } else {
                mapXml.put(nameVal[0], "");
            }

        }
        return mapXml;
    }

    public LinkedList<String> getFieldsList(LinkedHashMap<String, String> map) {
        Collection<String> list = map.values();
        LinkedList<String> outList = new LinkedList<String>();
        for (String s : list) {
            if (s.startsWith(LIBRARY_VALUES)) {
                outList.addAll(decodeCalcFields(s));
            } else {
                outList.addLast(s);
            }
        }
        return outList;
    }

    public LinkedList<String> translateFieldsList(LinkedList<String> tagList, LinkedHashMap<String, String> map) {
        LinkedList<String> outList = new LinkedList<String>();
        for (String s : tagList) {
            if (s.startsWith(LIBRARY_VALUES)) {
                outList.addAll(decodeCalcFields(s));
            } else {
                outList.addLast(map.get(s));
            }
        }
        return outList;
    }

    public LinkedList<String> decodeCalcFields(String fieldName) {
        LinkedList<String> outList = new LinkedList<String>();
        if ("lib:sysdate".equals(fieldName)) {
            return outList;
        } else if (fieldName.equals("lib:gender")) {
            return outList;
        }
        return outList;
    }

    public static LinkedList<String> splitSemicolons(String listCSV, String delimRegexp) {
        String[] arrList = listCSV.split(delimRegexp);
        LinkedList<String> list = new LinkedList<String>();
        for (String s : arrList) {
            list.addLast(s);
        }
        return list;
    }

    /**
     * Remove duplicates and text of XML elements
     */
    public void clearNormalize(Document doc, Node fromNode) {
        clearAllAttributes((Element) fromNode);
        NodeList foundNodes = fromNode.getChildNodes();
        if (countChildElements(foundNodes) == 0) {
            fromNode.setTextContent("");
        } else {
            while (true) {
                foundNodes = fromNode.getChildNodes();
                if (countChildElements(foundNodes) <= 1) {
                    clearNormalize(doc, getElementByIndex(foundNodes, 0));
                    break;
                }

                if (getElementByIndex(foundNodes, 0).getNodeName().equals(getElementByIndex(foundNodes, 1).getNodeName())) {
                    fromNode.removeChild(getElementByIndex(foundNodes, 1));
                } else {
                    for (int i = 0; i < countChildElements(foundNodes); i++) {
                        clearNormalize(doc, getElementByIndex(foundNodes, i));
                    }
                    return;
                }
            }
        }
    }

    public static void clearAllAttributes(Element element) {
        System.out.println("List attributes for node: " + element.getNodeName());
        // get a map containing the attributes of this node
        NamedNodeMap attributes = element.getAttributes();
        // get the number of nodes in this map
        int numAttrs = attributes.getLength();
        for (int i = 0; i < numAttrs; i++) {
            Attr attr = (Attr) attributes.item(i);
            String attrName = attr.getNodeName();

            String attrValue = attr.getNodeValue();
            System.out.println("       Found attribute: " + attrName + " with value: " + attrValue);
            attr.setValue("");

        }
    }

    /**
     * Return node name with context
     */
    private String getPathWithContext(Node fromNode, int level) {
        if (fromNode == null) {
            return null;
        }
        String path = fromNode.getNodeName();

        int nested = 0;
        Node parent = fromNode;
        while (++nested < level) {
            parent = getParentNode(parent);
            if (parent != null) {
                path = parent.getNodeName() + "/" + path;
            } else {
                break;
            }

        }
        return path;
    }

    /**
     * Store node name in a map of lost tags
     */
    public void storeLostTagInMap(Node node) {
        String name = node.getNodeName();
        if (lostTags.containsKey(name)) {

            System.out.println("INFO: Duplicate tag stored in map: " + name + " - " + node.getBaseURI());
            int i = 2;
            String nameOld = name;
            Node nodeOld = lostTags.remove(name);

            while (nameOld.equals(name)) {
                name = getPathWithContext(node, i);
                nameOld = getPathWithContext(nodeOld, i);
                ++i;
            }
            lostTags.put(name, node);
            lostTags.put(nameOld, node);

        } else {
            lostTags.put(name, node);
        }
    }

    public void printLostTags() {
        if (!lostTags.keySet().isEmpty()) {
            System.out.println("Lost tags to add to mapping:");
        }
        for (String lostTag : lostTags.keySet()) {
            System.out.println("\"" + lostTag + "=\\n\" +");
        }
    }

    /**
     * Get tables and fields list
     */
    public LinkedHashMap<String, LinkedList<String>> getTablesToFieldsRelation(LinkedHashMap<String, LinkedList<String>> dbStructureMap, Document doc, Node fromNode, LinkedHashMap<String, String> tagFieldMap, String tableName) {

        if (dbStructureMap == null) {
            dbStructureMap = new LinkedHashMap<String, LinkedList<String>>();
        }
        if (fromNode == null) {
            System.err.println("ERROR at getTablesToFieldsRelation(): " + fromNode + " is null at " + tableName);
            return dbStructureMap;
        }

        if (tableRootNodes == null) {
            tableRootNodes = new LinkedList<String>();
        }

        NodeList foundNodes = fromNode.getChildNodes();
        NamedNodeMap foundAttr = fromNode.getAttributes();
        if (countChildNodes(foundNodes, foundAttr) == 0) { //value node

            LinkedList<String> tmpList = dbStructureMap.get(tableName);//Field list for table
            String fieldName = getNodeFromMap(fromNode, tagFieldMap);

            if (tmpList == null) {
                tmpList = new LinkedList<String>();
            }
            if (fieldName != null) {
                if (!fieldName.startsWith(LIBRARY_VALUES) && !fieldName.startsWith(QUOTE)) //Skip library values
                {
                    if (!"".equals(fieldName)) {
                        if (tmpList.indexOf(fieldName) == -1) {
                            System.err.println("ERROR: repeated field: " + fieldName);
                        }
                        tmpList.addLast(fieldName);
                    }
                }
            } else {
                if (fromNode != null) {
                    System.out.println("\"" + (fromNode.getNodeType() == ATTRIBUTE_NODE ? "@" : "") + fromNode.getNodeName() + "=\\n\" +");
                    storeLostTagInMap(fromNode);
                } else {
                    System.out.println("\"" + "NULL" + "=\\n\" +");
                }
            }

            dbStructureMap.put(tableName, tmpList);
        } else {
            System.out.print("Node " + fromNode.getNodeName());
            int countChildren = countChildNodes(foundNodes, foundAttr);

            for (int i = 0; i < countChildren; i++) {
                Node currNode = getNodeByIndex(foundNodes, foundAttr, i);
                boolean childIsTableRow = isTableNode(currNode);
                String nodesTableName = !(childIsTableRow) ? tableName : mapXmlElementToTable(currNode.getNodeName());//ignore row tag name
                getTablesToFieldsRelation(dbStructureMap, doc, currNode, tagFieldMap, nodesTableName);
            }

        }
        return dbStructureMap;
    }

    /**
     * Get parent node for attribute and element nodes
     */
    private Node getParentNode(Node fromNode) {
        if (fromNode instanceof Attr) {
            return ((Attr) fromNode).getOwnerElement();
        } else {
            return fromNode.getParentNode();
        }

    }

    private String getNodeFromMap(Node fromNode, LinkedHashMap<String, String> map) {
        if (fromNode == null || map == null) {
            return null;
        }
        String path = fromNode.getNodeName();
        String fieldName = map.get(path);

        int nested = 0;
        Node parent = fromNode;
        while ((fieldName == null) && (++nested < MAX_PATH_LEN)) {//Max nesting
            parent = getParentNode(parent);
            if (parent != null) {
                path = parent.getNodeName() + "/" + path;
            } else {
                break;
            }
            fieldName = map.get(path);
        }

        return fieldName;
    }

    public int fillTemplate(IStringDataInterface dataIntf, LinkedHashMap<String, LinkedList<String>> res, Document template,
            Node fromNode, Node toNode, LinkedHashMap<String, String> map, String tableName, LinkedList<String> values, boolean isRowRoot, String parentTableName, String filterParent) {
        int dataRowCount = 0;
        if (res == null) {
            System.out.print("res is null at " + parentTableName);
            return dataRowCount;
        }

        if (fromNode == null) {
            System.out.print("fromNode is null at " + parentTableName);
            return dataRowCount;
        }

        System.out.println("fromNode: " + fromNode.getNodeName());

        NodeList foundNodes = fromNode.getChildNodes();
        NamedNodeMap foundAttr = fromNode.getAttributes();
        int iCountChildren = countChildNodes(foundNodes, foundAttr);
        if (iCountChildren == 0) { //value node

            String fieldName = getNodeFromMap(fromNode, map);

            if (fieldName != null) {
                if (!"".equals(fieldName)) {
                    if (fieldName.startsWith(QUOTE)) { //Get const fields
                        toNode.setTextContent(fieldName.substring(1, fieldName.length() - 1));
                    } else if (fieldName.startsWith(LIBRARY_VALUES)) { //Get library fields
                        toNode.setTextContent(getLibraryValues(fieldName, dataIntf, filterParent));
                    } else {

                        if (values != null) {
                            if (!values.isEmpty()) {

                                toNode.setTextContent(values.pop());
                                System.out.print(toNode.getNodeName() + "=" + toNode.getTextContent());
                            } else {
                                System.out.println("ERROR: List of fields is empty for: " + toNode.getNodeName() + "(" + tableName + ")");
                            }
                        } else {
                            if (toNode != null) {
                                System.out.println("ERROR: Fields list is null for: " + toNode.getNodeName() + "(" + tableName + ")");
                                toNode.setTextContent("ERROR! for table: " + tableName);
                            } else {
                                System.out.println("ERROR: toNode is null");
                            }
                        }
                    }
                } else {
                    toNode.setTextContent("ERROR! Empty field name for table: " + tableName);
                }

            }

        } else {
            System.out.println();
            System.out.print("NODE " + fromNode.getNodeName() + ": ");

            LinkedHashMap<Integer, LinkedList<String>> data = null;
            LinkedList<String> row = null;
            NodeList newNodes = null;
            NamedNodeMap newAttr = null;
            if (isRowRoot) {
                System.out.println("-ROW ROOT");
                LinkedList<String> fields = res.get(tableName);
                if (fields != null) {
                    data = dataIntf.getData(tableName, parentTableName, fields, filterParent);
                } else {
                    System.out.print("Error getting fields for table: " + tableName + " Node: " + fromNode.getNodeName());
                }
                dataRowCount = data.keySet().size();
                for (Integer rowNo : data.keySet()) {
                    System.out.println("ROW " + rowNo + " OF " + fromNode.getNodeName());
                    row = data.get(rowNo);
                    LinkedList<String> rowCopy = (LinkedList<String>) row.clone();
                    Node newNode = cloneNode(template, fromNode);

                    newNodes = newNode.getChildNodes();
                    newAttr = newNode.getAttributes();
                    iCountChildren = countChildNodes(newNodes, newAttr); //TODO remove as it is the same
                    for (int i = 0; i < iCountChildren; i++) {
                        Node currNode = getNodeByIndex(newNodes, newAttr, i);
                        fillTemplate(dataIntf, res, template, currNode, currNode, map, tableName, rowCopy, false, parentTableName, rowNo.toString());
                    }
                }
                if (getParentNode(fromNode).getNodeType() != DOCUMENT_NODE) {
                    getParentNode(fromNode).removeChild(fromNode);
                }

            } else { //if (countChildNodes == 1) // TABLE ROOT branch
                newNodes = toNode.getChildNodes(); //!
                newAttr = toNode.getAttributes();
                int offset = 0;
                for (int i = 0; i < iCountChildren; i++) { //Table root has one child only - ROW ROOT
                    if (i != 0) { //
                        System.out.println("ERROR! isTableRoot() returned wrong value");
                    }
                    Node currChild = null;
                    if (offset == 0) {
                        currChild = getNodeByIndex(newNodes, newAttr, i);
                    } else {
                        currChild = getNodeByIndex(newNodes, newAttr, i, offset - 1);
                    }
                    boolean childIsTableRow = isTableNodeCached(currChild);
                    String nodesTableName = !childIsTableRow ? tableName : mapXmlElementToTable(currChild.getNodeName());//ignore row tag name
                    System.out.println(childIsTableRow ? "-TABLE ROOT" : "CHAIN ROOT DETECTED!!!");

                    String newTableParent = childIsTableRow ? tableName : parentTableName;
                    offset = fillTemplate(dataIntf, res, template, currChild, currChild, map, nodesTableName, values, childIsTableRow, newTableParent, filterParent);
                }
            }
        }
        return dataRowCount;
    }

    /**
     * True if there is 1 child that has a child-value (or 1 attribute, but not
     * used)
     */
    private boolean isTableNode(Node node) {
        if (tableRootNodes != null && tableRootNodes.size() > 0) {
            return isTableNodeCached(node);
        }

        NodeList foundNodes = node.getChildNodes();
        int count = countChildElements(foundNodes);
        if (count == 1) {
            foundNodes = getElementByIndex(foundNodes, 0).getChildNodes();
            count = countChildElements(foundNodes);
            for (int i = 0; i < count; i++) {
                if (0 == countChildElements(getElementByIndex(foundNodes, i).getChildNodes())) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Returns the value stored in getTablesToFieldsRelation
     * functions isTableRoot, without recalculating */
    private boolean isTableNodeCached(Node node) {
        return (tableRootNodes.indexOf(node.getNodeName())) != -1;
    }

    public void fillNode(Document doc, String parentPath, String value) {
        Node node = findNode(doc, parentPath);
        if (node != null) {
            node.setTextContent(value);
        }
    }

    /**
     * returns same node for top level
     */
    public Node cloneNode(Document doc, Node node) {
        if (node != null) {
            if (getParentNode(node).getNodeType() == DOCUMENT_NODE) {
                return node;
            }
            Node newNode = node.cloneNode(true);
            Node nnn = getParentNode(node).insertBefore(newNode, node);
            //System.out.println("After clone " + node.getNodeName());
            //System.out.println(docToString(doc));
            return nnn;

        }
        return null;
    }

    public Node findNode(Document doc, String parentPath) {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList foundNodes = (NodeList) xPath.evaluate(
                    parentPath, doc,
                    XPathConstants.NODESET);
            if (foundNodes.getLength() > 0) {
                return foundNodes.item(0);
            }
        } catch (Exception e) {
            LogFactory.getLog().error("findNode error", e);
        }
        return null;
    }

    public static Document loadXML(String xml) throws Exception {
        Document doc = null;
        if (xml == null) {
            return doc;
        }
        try {
            InputSource is = new InputSource(new StringReader(xml));
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = null;
            builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DomErrorHandler());
            doc = builder.parse(is);
        } catch (Throwable e) {
            throw new Exception("XML cannot be created from xml string = " + xml, e);
        }
        return doc;
    }

    public static Document loadXMLFromResource(String resource) throws Exception {
        Document doc = null;
        if (resource == null) {
            return doc;
        }
        try {
            InputSource is = new InputSource(TestMappedXmlReport.class.getResourceAsStream(resource));
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = null;
            builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DomErrorHandler());
            doc = builder.parse(is);
        } catch (Throwable e) {
            throw new Exception("XML cannot be created from resource: " + resource, e);
        }
        return doc;
    }

    /**
     * @param xml
     * @return
     * @throws Exception
     */
    public static Document loadXMLFrom(String xml) {
        Document doc = null;
        try {
            doc = loadXML(xml);
        } catch (Exception e) {
            LogFactory.getLog().error(e);
        }
        return doc;
    }

    public static String docToString(Document xml) {
        StreamResult result = null;
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,
                    "no");

            // initialize StreamResult with File object to save to file
            result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(xml);
            transformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            LogFactory.getLog().error(
                    "Error in Tools method convertDomToString(): ", e);
        } catch (TransformerFactoryConfigurationError e) {
            LogFactory.getLog().error(
                    "Error in Tools method convertDomToString(): ", e);
        } catch (TransformerException e) {
            LogFactory.getLog().error(
                    "Error in Tools method convertDomToString(): ", e);
        } catch (Throwable e) {
            LogFactory.getLog().error(
                    "Error in Tools method convertDomToString(): ", e);
        }
        String xmlString = result.getWriter().toString();

        return xmlString;
    }

    /**
     * Run report generation and create XML as DOM document.
     */
    public Document run(Document template) {
        tables = parseMap(tableMapping);
        tableRootNodes = new LinkedList<String>();
        tableRootNodes.addAll(tables.keySet());
        LinkedHashMap<String, String> map = parseMap(fieldMapping);
        String threadName = Thread.currentThread().getName();
        LogFactory.getLog().debug(
                "***************** Message from " + threadName + ": " + new Date().getTime() + " *************");
        clearNormalize(template, template.getDocumentElement()); //TODO Remove this call
        LinkedHashMap<String, LinkedList<String>> fls = getTablesToFieldsRelation(null, template, template.getDocumentElement(), map, startFrom);
        System.out.println(fls.toString());
        System.out.println("TableNodes:" + tableRootNodes.toString());
        //TODO Set filter value
        fillTemplate(new TestStringDataInterfaceImpl(), fls, template, template.getDocumentElement(), template.getDocumentElement(), map, mapXmlElementToTable(template.getDocumentElement().getNodeName()), null, isTableNodeCached(template.getDocumentElement()), "", "firm_id=123");
        printLostTags();
        return template;
    }
}
