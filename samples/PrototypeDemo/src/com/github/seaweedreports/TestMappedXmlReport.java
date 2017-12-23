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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Test XML report class
 */
public class TestMappedXmlReport {

    public static final String LIBRARY_VALUES = "lib:";

    // First table TODO eliminate this property
    public static final String startFrom = "firms";

    // Empty XML
    // in this example it is filled with values  intentionally
    public static final String xmlDummy
            = "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n"
            + " <employer>\n"
            + "  <name>Seaweed inc.</name>\n"
            + "  <employee>\n"
            + "  <!-- ====== Employee ====== -->\n"
            + "    <!--  Employee's name  -->\n"
            + "    <name>John</name>\n"
            + "    <!--  Employee's surname  -->\n"
            + "    <surname>Weed</surname>\n"
            + "    <age>18</age>\n"
            + "    <input_date>31.12.2011</input_date>\n"
            + "    <something></something>\n"
            + "  </employee>\n"
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
            + "employee/name=first_name\n"
            + "surname=family_name\n"
            + "employee/input_date=lib:sysdate\n"
            + "something=\n"
            + "debt=lib:my_zero\n"
            + "age=age\n";

    private LinkedList<String> tableRootNodes = null;

    private HashMap<String, String> tables;

    //--------------------- User functions ------------------------------
    /** Empty string checking util */
    public static boolean isEmpty(final String s) {
        return s == null || s.trim().length() == 0;
    }
    /** Date formatting util */
    public static String formatDate(Date value, String format) {
        if (value == null || isEmpty(format)) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        String today = sdf.format(value);
        return today;
    }
    /** Implement constants and library functions here */
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

    public int countChildNodes(NodeList foundNodes) {
        int counter = 0;
        for (int i = 0; i < foundNodes.getLength(); i++) {
            if (foundNodes.item(i).getNodeType() == 1) {
                counter++;
            }
        }
        return counter;
    }

    public Node getNodeByIndex(NodeList foundNodes, int index) {

        int counter = -1;
        for (int i = 0; i < foundNodes.getLength(); i++) {

            if (foundNodes.item(i).getNodeType() == 1) {
                counter++;
            }
            if (counter == index) {
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

        NodeList foundNodes = fromNode.getChildNodes();
        if (countChildNodes(foundNodes) == 0) {
            fromNode.setTextContent("");
        } else {
            while (true) {
                foundNodes = fromNode.getChildNodes();
                if (countChildNodes(foundNodes) <= 1) {
                    clearNormalize(doc, getNodeByIndex(foundNodes, 0));
                    break;
                }

                if (getNodeByIndex(foundNodes, 0).getNodeName().equals(getNodeByIndex(foundNodes, 1).getNodeName())) {
                    fromNode.removeChild(getNodeByIndex(foundNodes, 1));
                } else {
                    for (int i = 0; i < countChildNodes(foundNodes); i++) {
                        clearNormalize(doc, getNodeByIndex(foundNodes, i));
                    }
                    return;
                }
            }
        }
    }

    /**
     * Get tables and fields list
     */
    public LinkedHashMap<String, LinkedList<String>> getTablesToFieldsRelation(LinkedHashMap<String, LinkedList<String>> dbStructureMap, Document doc, Node fromNode, LinkedHashMap<String, String> tagFieldMap, String tableName) {
        if (dbStructureMap == null) {
            dbStructureMap = new LinkedHashMap<String, LinkedList<String>>();
        }
        if (tableRootNodes == null) {
            tableRootNodes = new LinkedList<String>();
        }

        NodeList foundNodes = fromNode.getChildNodes();
        if (countChildNodes(foundNodes) == 0) { //value node

            LinkedList<String> tmpList = dbStructureMap.get(tableName);//Field list for table
            String fieldName = tagFieldMap.get(fromNode.getNodeName());
            if (tmpList == null) {
                tmpList = new LinkedList<String>();
            }
            if (fieldName == null) {//repeated fields
                fieldName = tagFieldMap.get(fromNode.getParentNode().getNodeName() + "/" + fromNode.getNodeName());
            }
            if (fieldName != null) {
                if (!fieldName.startsWith(LIBRARY_VALUES)) //Skip library values
                {
                    if (fieldName.length() > 0) {
                        if (tmpList.indexOf(fieldName) == -1) {
                            tmpList.addLast(fieldName);
                        }
                    }
                }
            } else {
                System.out.println("\"" + fromNode.getNodeName() + "=\\n\" +");
            }

            dbStructureMap.put(tableName, tmpList);
        } else {
            System.out.print("Node " + fromNode.getNodeName());
            int countChildren = countChildNodes(foundNodes);
            boolean childIsTableRow = isTableNode(fromNode);
            if (childIsTableRow) {
                tableRootNodes.addLast(fromNode.getNodeName());
            }

            String nodesTableName = !(childIsTableRow) ? tableName : mapXmlElementToTable(fromNode.getNodeName());//ignore row tag name

            for (int i = 0; i < countChildren; i++) {

                getTablesToFieldsRelation(dbStructureMap, doc, getNodeByIndex(foundNodes, i), tagFieldMap, nodesTableName);
            }

        }
        return dbStructureMap;
    }

    public void fillTemplate(IStringDataInterface dataAdapter, LinkedHashMap<String, LinkedList<String>> res, Document template,
            Node fromNode, Node toNode, LinkedHashMap<String, String> map, String tableName, LinkedList<String> values, boolean isRowRoot, String parentTableName, String filterParent) {
        if (res == null) {
            return;
        }

        NodeList foundNodes = fromNode.getChildNodes();
        int iCountChildren = countChildNodes(foundNodes);
        if (iCountChildren == 0) { //value node

            String fieldName = map.get(fromNode.getNodeName());
            if (fieldName == null) {
                fieldName = map.get(fromNode.getParentNode().getNodeName() + "/" + fromNode.getNodeName());
            }
            if (fieldName != null) {
                if (fieldName.length() > 0) {
                    if (fieldName.startsWith(LIBRARY_VALUES)) { //Get library fields
                        toNode.setTextContent(getLibraryValues(fieldName, dataAdapter, filterParent));

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
                }

            }

        } else {
            System.out.println();
            System.out.print("NODE " + fromNode.getNodeName() + ": ");

            LinkedHashMap<Integer, LinkedList<String>> data = null;
            LinkedList<String> row = null;
            NodeList newNodes = null;
            if (isRowRoot) {

                LinkedList<String> fields = res.get(tableName);
                if (fields != null) {
                    data = dataAdapter.getData(tableName, parentTableName/*getNodeByIndex(foundNodes, 0).getParentNode().getNodeName()*/, fields, filterParent);
                } else {
                    System.out.print("Error getting fields for table: " + tableName + " Node: " + fromNode.getNodeName());
                }

                for (Integer rowNo : data.keySet()) {
                    row = data.get(rowNo);
                    LinkedList<String> rowCopy = (LinkedList<String>) row.clone();
                    Node newNode = cloneNode(template, fromNode);
                    Element elem = (Element) newNode;
                    elem.setAttribute("cloned" + rowNo, "cloned" + rowNo);
                    newNodes = newNode.getChildNodes();

                    for (int i = 0; i < iCountChildren; i++) {
                        fillTemplate(dataAdapter, res, template, getNodeByIndex(foundNodes, i), getNodeByIndex(newNodes, i), map, tableName, rowCopy, false, parentTableName, rowNo.toString());
                    }
                }
                fromNode.getParentNode().removeChild(fromNode);
                System.out.println("After removeChild " + fromNode.getNodeName());
                System.out.println(docToString(template));
            } else { //if (countChildNodes == 1)
                for (int i = 0; i < iCountChildren; i++) {
                    if (i != 0) {
                        LogFactory.getLog().error("ERROR! isTableRoot() returned wrong value");
                    }
                    boolean childIsTableRow = isTableNodeCached(toNode);
                    String nodesTableName = !childIsTableRow ? tableName : mapXmlElementToTable(fromNode.getNodeName());//ignore row tag name

                    Element elem = (Element) fromNode;
                    elem.setAttribute("TABLE", childIsTableRow ? "-TABLE ROOT" : "CHAIN ROOT DETECTED!!!");

                    String newTableParent = childIsTableRow ? tableName : parentTableName;
                    newNodes = toNode.getChildNodes();
                    fillTemplate(dataAdapter, res, template, getNodeByIndex(newNodes, i), null /*getNodeByIndex(foundNodes, i)*/, map, nodesTableName, row, childIsTableRow, newTableParent, filterParent);
                }
            }
        }
    }

    /**
     * True if there is 1 child that has a child-value (or 1 attribute, but not
     * used)
     */
    private boolean isTableNode(Node node) {
        NodeList foundNodes = node.getChildNodes();
        int count = countChildNodes(foundNodes);
        if (count == 1) {
            foundNodes = getNodeByIndex(foundNodes, 0).getChildNodes();
            count = countChildNodes(foundNodes);
            for (int i = 0; i < count; i++) {
                if (0 == countChildNodes(getNodeByIndex(foundNodes, i).getChildNodes())) {
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

    public Node cloneNode(Document doc, Node node) {
        if (node != null) {
            Node newNode = node.cloneNode(true);
            Node nnn = node.getParentNode().appendChild(newNode);
            return nnn;
        }
        return null;
    }

    public Node cloneNode(Document doc, String parentPath) {
        Node node = findNode(doc, parentPath);
        if (node != null) {
            Node newNode = node.cloneNode(true);
            return node.getParentNode().appendChild(newNode);
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
    public Document run() {
        tables = parseMap(tableMapping);
        LinkedHashMap<String, String> map = parseMap(fieldMapping);
        String threadName = Thread.currentThread().getName();
        LogFactory.getLog().debug(
                "***************** Message from " + threadName + ": " + new Date().getTime() + " *************");

        Document template = loadXMLFrom(xmlDummy);
        clearNormalize(template, template.getDocumentElement());
        LinkedHashMap<String, LinkedList<String>> fls = getTablesToFieldsRelation(null, template, template.getDocumentElement(), map, startFrom);
        System.out.println(fls.toString());
        System.out.println("TableNodes:" + tableRootNodes.toString());
        fillTemplate(new TestStringDataInterfaceImpl(), fls, template, template.getDocumentElement(), template.getDocumentElement(), map, "", null, false, "", "");
        return template;
    }
}
