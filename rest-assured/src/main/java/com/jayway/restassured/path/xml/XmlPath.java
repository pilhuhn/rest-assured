/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jayway.restassured.path.xml;

import com.jayway.restassured.assertion.XMLAssertion;
import com.jayway.restassured.exception.ParsePathException;
import com.jayway.restassured.internal.support.Prettifier;
import com.jayway.restassured.path.xml.element.Node;
import com.jayway.restassured.path.xml.element.NodeChildren;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang3.Validate;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;

import static com.jayway.restassured.assertion.AssertParameter.notNull;
import static com.jayway.restassured.internal.path.ObjectConverter.convertObjectTo;
import static com.jayway.restassured.path.xml.XmlPath.CompatibilityMode.XML;

/**
 * XmlPath is an alternative to using XPath for easily getting values from an XML document. It follows the Groovy syntax
 * described <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">here</a>. <br>Let's say we have an XML defined as;
 * <pre>
 * &lt;shopping&gt;
 &lt;category type=&quot;groceries&quot;&gt;
 &lt;item&gt;
 &lt;name&gt;Chocolate&lt;/name&gt;
 &lt;price&gt;10&lt;/price&gt;
 &lt;/item&gt;
 &lt;item&gt;
 &lt;name&gt;Coffee&lt;/name&gt;
 &lt;price&gt;20&lt;/price&gt;
 &lt;/item&gt;
 &lt;/category&gt;
 &lt;category type=&quot;supplies&quot;&gt;
 &lt;item&gt;
 &lt;name&gt;Paper&lt;/name&gt;
 &lt;price&gt;5&lt;/price&gt;
 &lt;/item&gt;
 &lt;item quantity=&quot;4&quot;&gt;
 &lt;name&gt;Pens&lt;/name&gt;
 &lt;price&gt;15&lt;/price&gt;
 &lt;/item&gt;
 &lt;/category&gt;
 &lt;category type=&quot;present&quot;&gt;
 &lt;item when=&quot;Aug 10&quot;&gt;
 &lt;name&gt;Kathryn&#39;s Birthday&lt;/name&gt;
 &lt;price&gt;200&lt;/price&gt;
 &lt;/item&gt;
 &lt;/category&gt;
 &lt;/shopping&gt;
 * </pre>
 *
 * Get the name of the first category item:
 * <pre>
 *     String name = with(XML).get("shopping.category.item[0].name");
 * </pre>
 *
 * To get the number of category items:
 * <pre>
 *     int items = with(XML).get("shopping.category.item.size()");
 * </pre>
 *
 * Get a specific category:
 * <pre>
 *     Node category = with(XML).get("shopping.category[0]");
 * </pre>
 *
 * To get the number of categories with type attribute equal to 'groceries':
 * <pre>
 *    int items = with(XML).get("shopping.category.findAll { it.@type == 'groceries' }.size()");
 * </pre>
 *
 * Get all items with price greater than or equal to 10 and less than or equal to 20:
 * <pre>
 * List&lt;Node&gt; itemsBetweenTenAndTwenty = with(XML).get("shopping.category.item.findAll { item -> def price = item.price.toFloat(); price >= 10 && price <= 20 }");
 * </pre>
 *
 * Get the chocolate price:
 * <pre>
 * int priceOfChocolate = with(XML).getInt("**.find { it.name == 'Chocolate' }.price"
 * </pre>
 *
 * You can also parse HTML by setting compatibility mode to HTML:
 * <pre>
 * XmlPath xmlPath = new XmlPath(CompatibilityMode.HTML,&lt;some html&gt;);
 * </pre>
 */
public class XmlPath {

    private final CompatibilityMode mode;
    private final GPathResult input;

    private String rootPath = "";

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param text The text containing the XML document
     */
    public XmlPath(String text) {
        this(XML, text);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param stream The stream containing the XML document
     */
    public XmlPath(InputStream stream) {
        this(XML, stream);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param source The source containing the XML document
     */
    public XmlPath(InputSource source) {
        this(XML, source);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param file The file containing the XML document
     */
    public XmlPath(File file) {
        this(XML, file);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param reader The reader containing the XML document
     */
    public XmlPath(Reader reader) {
        this(XML, reader);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param uri The URI containing the XML document
     */
    public XmlPath(URI uri) {
        this(XML, uri);
    }


    /**
     * Instantiate a new XmlPath instance.
     *
     * @param mode The compatibility mode
     * @param text The text containing the XML document
     */
    public XmlPath(CompatibilityMode mode, String text) {
        Validate.notNull(mode, "Compatibility mode cannot be null");
        this.mode = mode;
        input = parseText(text);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param mode The compatibility mode
     * @param stream The stream containing the XML document
     */
    public XmlPath(CompatibilityMode mode, InputStream stream) {
        Validate.notNull(mode, "Compatibility mode cannot be null");
        this.mode = mode;
        input = parseInputStream(stream);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param mode The compatibility mode
     * @param source The source containing the XML document
     */
    public XmlPath(CompatibilityMode mode, InputSource source) {
        Validate.notNull(mode, "Compatibility mode cannot be null");
        this.mode = mode;
        input = parseInputSource(source);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param mode The compatibility mode
     * @param file The file containing the XML document
     */
    public XmlPath(CompatibilityMode mode, File file) {
        Validate.notNull(mode, "Compatibility mode cannot be null");
        this.mode = mode;
        input = parseFile(file);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param mode The compatibility mode
     * @param reader The reader containing the XML document
     */
    public XmlPath(CompatibilityMode mode, Reader reader) {
        Validate.notNull(mode, "Compatibility mode cannot be null");
        this.mode = mode;
        input = parseReader(reader);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param mode The compatibility mode
     * @param uri The URI containing the XML document
     */
    public XmlPath(CompatibilityMode mode, URI uri) {
        Validate.notNull(mode, "Compatibility mode cannot be null");
        this.mode = mode;
        input = parseURI(uri);
    }

    /**
     * Get the entire XML graph as an Object
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @return The XML Node. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public Node get() {
        return (Node) get("$");
    }

    /**
     * Get the result of an XML path expression. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @param <T> The type of the return value.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <T> T get(String path) {
        notNull(path, "path");
        final XMLAssertion xmlAssertion = new XMLAssertion();
        final String root = rootPath.equals("") ? rootPath : rootPath.endsWith(".") ? rootPath : rootPath + ".";
        xmlAssertion.setKey(root + path);
        return (T) xmlAssertion.getResult(input);
    }

    /**
     * Get the result of an XML path expression as a list. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @param <T> The list type
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <T> List<T> getList(String path) {
        return getAsList(path);
    }

    /**
     * Get the result of an XML path expression as a list. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @param genericType The generic list type
     * @param <T> The type
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <T> List<T> getList(String path, Class<T> genericType) {
        return getAsList(path, genericType);
    }

    /**
     * Get the result of an XML path expression as a map. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @param <K> The type of the expected key
     * @param <V> The type of the expected value
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <K,V> Map<K, V> getMap(String path) {
        return get(path);
    }

    /**
     * Get the result of an XML path expression as a map. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @param keyType The type of the expected key
     * @param valueType The type of the expected value
     * @param <K> The type of the expected key
     * @param <V> The type of the expected value
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <K,V> Map<K, V> getMap(String path, Class<K> keyType, Class<V> valueType) {
        final Map<K, V> originalMap = get(path);
        final Map<K, V> newMap = new HashMap<K, V>();
        for (Entry<K, V> entry : originalMap.entrySet()) {
            final K key = entry.getKey() == null ? null : convertObjectTo(entry.getKey(), keyType);
            final V value = entry.getValue() == null ? null : convertObjectTo(entry.getValue(), valueType);
            newMap.put(key, value);
        }
        return Collections.unmodifiableMap(newMap);
    }

    /**
     * Get the result of an XML path expression as an int. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public int getInt(String path) {
        final Object object = get(path);
        return convertObjectTo(object, Integer.class);
    }

    /**
     * Get the result of an XML path expression as a boolean. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public boolean getBoolean(String path) {
        Object object = get(path);
        return convertObjectTo(object, Boolean.class);
    }

    /**
     * Get the result of an XML path expression as a {@link Node}. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public Node getNode(String path) {
        return convertObjectTo(get(path), Node.class);
    }

    /**
     * Get the result of an XML path expression as a {@link com.jayway.restassured.path.xml.element.NodeChildren}. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public NodeChildren getNodeChildren(String path) {
        return convertObjectTo(get(path), NodeChildren.class);
    }

    /**
     * Get the result of an XML path expression as a char. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public char getChar(String path) {
        Object object = get(path);
        return convertObjectTo(object, Character.class);
    }

    /**
     * Get the result of an XML path expression as a byte. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public byte getByte(String path) {
        Object object = get(path);
        return convertObjectTo(object, Byte.class);
    }

    /**
     * Get the result of an XML path expression as a short. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public short getShort(String path) {
        Object object = get(path);
        return convertObjectTo(object, Short.class);
    }

    /**
     * Get the result of an XML path expression as a float. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public float getFloat(String path) {
        Object object = get(path);
        return convertObjectTo(object, Float.class);
    }

    /**
     * Get the result of an XML path expression as a double. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public double getDouble(String path) {
        Object object = get(path);
        return convertObjectTo(object, Double.class);
    }

    /**
     * Get the result of an XML path expression as a long. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public long getLong(String path) {
        Object object = get(path);
        return convertObjectTo(object, Long.class);
    }

    /**
     * Get the result of an XML path expression as a string. For syntax details please refer to
     * <a href="http://groovy.codehaus.org/Updating+XML+with+XmlSlurper">this</a> url.
     *
     * @param path The XML path.
     * @return The object matching the XML path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public String getString(String path) {
        Object object = get(path);
        return convertObjectTo(object, String.class);
    }

    /**
     * Get the XML as a prettified string.
     *
     * @return The XML as a prettified String.
     */
    public String prettify() {
        return new Prettifier().prettify(input);
    }

    /**
     * Get and print the XML as a prettified string.
     *
     * @return The XML as a prettified String.
     */
    public String prettyPrint() {
        final String pretty = prettify();
        System.out.println(pretty);
        return pretty;
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param text The text containing the XML document
     */
    public static XmlPath given(String text) {
        return new XmlPath(text);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param stream The stream containing the XML document
     */
    public static XmlPath given(InputStream stream) {
        return new XmlPath(stream);
    }
    /**
     * Instantiate a new XmlPath instance.
     *
     * @param source The source containing the XML document
     */
    public static XmlPath given(InputSource source) {
        return new XmlPath(source);
    }
    /**
     * Instantiate a new XmlPath instance.
     *
     * @param file The file containing the XML document
     */
    public static XmlPath given(File file) {
        return new XmlPath(file);
    }
    /**
     * Instantiate a new XmlPath instance.
     *
     * @param reader The reader containing the XML document
     */
    public static XmlPath given(Reader reader) {
        return new XmlPath(reader);
    }
    /**
     * Instantiate a new XmlPath instance.
     *
     * @param uri The URI containing the XML document
     */
    public static XmlPath given(URI uri) {
        return new XmlPath(uri);
    }
    public static XmlPath with(InputStream stream) {
        return new XmlPath(stream);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param text The text containing the XML document
     */
    public static XmlPath with(String text) {
        return new XmlPath(text);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param source The source containing the XML document
     */
    public static XmlPath with(InputSource source) {
        return new XmlPath(source);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param file The file containing the XML document
     */
    public static XmlPath with(File file) {
        return new XmlPath(file);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param reader The reader containing the XML document
     */
    public static XmlPath with(Reader reader) {
        return new XmlPath(reader);
    }
    /**
     * Instantiate a new XmlPath instance.
     *
     * @param uri The URI containing the XML document
     */
    public static XmlPath with(URI uri) {
        return new XmlPath(uri);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param stream The stream containing the XML document
     */
    public static XmlPath from(InputStream stream) {
        return new XmlPath(stream);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param text The text containing the XML document
     */
    public static XmlPath from(String text) {
        return new XmlPath(text);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param source The source containing the XML document
     */
    public static XmlPath from(InputSource source) {
        return new XmlPath(source);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param file The file containing the XML document
     */
    public static XmlPath from(File file) {
        return new XmlPath(file);
    }

    /**
     * Instantiate a new XmlPath instance.
     *
     * @param reader The reader containing the XML document
     */
    public static XmlPath from(Reader reader) {
        return new XmlPath(reader);
    }
    /**
     * Instantiate a new XmlPath instance.
     *
     * @param uri The URI containing the XML document
     */
    public static XmlPath from(URI uri) {
        return new XmlPath(uri);
    }

    private GPathResult parseText(final String text)  {
        return new ExceptionCatcher() {
            protected GPathResult method(XmlSlurper slurper) throws Exception {
                return slurper.parseText(text);
            }
        }.invoke();
    }

    /**
     * Set the root path of the document so that you don't need to write the entire path. E.g.
     * <pre>
     * final XmlPath xmlPath = new XmlPath(XML).setRoot("shopping.category.item");
     * assertThat(xmlPath.getInt("size()"), equalTo(5));
     * assertThat(xmlPath.getList("children().list()", String.class), hasItem("Pens"));
     * </pre>
     *
     * @param rootPath The root path to use.
     */
    public XmlPath setRoot(String rootPath) {
        notNull(rootPath, "Root path");
        this.rootPath = rootPath;
        return this;
    }

    private <T> List<T> getAsList(String path) {
        return getAsList(path, null);
    }

    private <T> List<T> getAsList(String path, final Class<?> explicitType) {
        Object returnObject = get(path);
        if(returnObject instanceof NodeChildren) {
            final NodeChildren nodeChildren = (NodeChildren) returnObject;
            returnObject =  Collections.unmodifiableList(new ArrayList<T>(CollectionUtils.collect(nodeChildren.list(), new Transformer() {
                public Object transform(Object input) {
                    if(explicitType == null) {
                        return input.toString();
                    }
                    return convertObjectTo(input, explicitType);
                }
            })));
        } else if(!(returnObject instanceof List)) {
            final List<T> asList = new ArrayList<T>();
            if(returnObject != null) {
                final T e;
                if(explicitType == null) {
                    e = (T) returnObject.toString();
                } else {
                    e =  (T) convertObjectTo(returnObject, explicitType);
                }
                asList.add(e);
            }
            returnObject = asList;
        } else if(explicitType != null) {
            final List<?> returnObjectAsList = (List<?>) returnObject;
            final List<T> convertedList = new ArrayList<T>();
            for (Object o : returnObjectAsList) {
                convertedList.add((T) convertObjectTo(o, explicitType));
            }
            returnObject = convertedList;
        }
        return returnObject == null ? null : Collections.unmodifiableList((List<T>) returnObject);
    }

    private GPathResult parseInputStream(final InputStream stream)  {
        return new ExceptionCatcher() {
            protected GPathResult method(XmlSlurper slurper) throws Exception {
                return slurper.parse(stream);
            }
        }.invoke();
    }

    private GPathResult parseReader(final Reader reader)  {
        return new ExceptionCatcher() {
            protected GPathResult method(XmlSlurper slurper) throws Exception {
                return slurper.parse(reader);
            }
        }.invoke();
    }

    private GPathResult parseFile(final File file)  {
        return new ExceptionCatcher() {
            protected GPathResult method(XmlSlurper slurper) throws Exception {
                return slurper.parse(file);
            }
        }.invoke();
    }

    private GPathResult parseURI(final URI uri)  {
        return new ExceptionCatcher() {
            protected GPathResult method(XmlSlurper slurper) throws Exception {
                return slurper.parse(uri.toString());
            }
        }.invoke();
    }

    private GPathResult parseInputSource(final InputSource source)  {
        return new ExceptionCatcher() {
            protected GPathResult method(XmlSlurper slurper) throws Exception {
                return slurper.parse(source);
            }
        }.invoke();
    }

    private abstract class ExceptionCatcher {

        protected abstract GPathResult method(XmlSlurper slurper) throws Exception;

        public GPathResult invoke() {
            try {
                final XmlSlurper slurper;
                if(mode == XML) {
                    slurper = new XmlSlurper();
                } else {
                    XMLReader p = new org.ccil.cowan.tagsoup.Parser();
                    slurper = new XmlSlurper(p);
                }
                return method(slurper);
            } catch(Exception e) {
                throw new ParsePathException("Failed to parse the XML document", e);
            }
        }
    }

    public static enum CompatibilityMode {
        XML, HTML
    }
}