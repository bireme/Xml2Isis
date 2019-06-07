/*=========================================================================

    Xml2Isis © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/Xml2Isis/blob/master/LICENSE.txt

  ==========================================================================*/
/*
 * StaxXmlWalker.java
 *
 * Created on 28/08/2007, 13:37:22
 *
 */

package br.bireme.xml2isis;

import bruma.BrumaException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 *
 * @author Heitor Barbieri
 */
class StaxXmlWalker {
    static final String DEFAULT_ENCODING = "ISO-8859-1";
    static final String DEFAULT_EMPTY_FIELD = "";

    static final int REPLACE_CHAR = 512;

    private XMLStreamReader parser;
    private XPathTree xpath;
    private IsisWriter writer;
    private String fileName;
    private boolean createMissFld;
    private boolean createFileNameFld;
    private boolean allowSubElems;

    StaxXmlWalker(final File xml,
                  final XPathTree xpath,
                  final IsisWriter writer,
                  final boolean createMissFld,
                  final boolean allowSubElems,   // include subelements inside a element, f ex, <abstract>xxx <b>cc</b> yyy</abstract>
                  final String encoding) throws XMLStreamException,
                                                IOException {
        if (xml == null) {
            throw new IllegalArgumentException();
        }
        if (xpath == null) {
            throw new IllegalArgumentException();
        }
        if (writer == null) {
            throw new IllegalArgumentException();
        }
        final String enc = (encoding == null)
                            ? System.getProperty("file.encoding") : encoding;
        //System.setProperty("javax.xml.stream.XMLInputFactory","com.bea.xml.stream.MXParserFactory");

        final XMLInputFactory factory = XMLInputFactory.newInstance();

        factory.setProperty("javax.xml.stream.isReplacingEntityReferences",//XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES,
                                                                 //Boolean.TRUE);
                                                                 Boolean.FALSE);
        factory.setProperty("javax.xml.stream.isCoalescing", Boolean.TRUE);
/*System.out.println("supported=" + factory.isPropertySupported(
                            XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES));*/
        this.xpath = xpath;
        this.writer = writer;
        /*this.parser = factory.createXMLStreamReader(
                                                 new FileInputStream(xml), enc);*/
        this.parser = factory.createXMLStreamReader(
                new ReplaceBufferedReader(
                       new InputStreamReader(new FileInputStream(xml), enc)));
        this.fileName = xml.getCanonicalPath();
        this.createMissFld = createMissFld;
        this.allowSubElems = allowSubElems;
        this.createFileNameFld = false;
    }

    void close() throws XMLStreamException {
        if (parser != null) {
            parser.close();
        }
    }

    void createFileNameField(final boolean opt) {
        createFileNameFld = opt;
    }

    void convert() throws XMLStreamException, BrumaException {
        String name;
        final XPathTree.TreeElement root = xpath.getRoot();
        final int saveLevel = xpath.getSaveLevel();
        XPathTree.TreeElement current = null;
        XPathTree.TreeElement aux;
        int eventType;
        int curLevel = 0; // root level is 1
        int skipLevel = Integer.MAX_VALUE;
        boolean hasNext = true;
        StringBuilder builder;
        String buffer;
        QName qname;
        String prefix;

        writer.newRecord();

        while (hasNext) {
            eventType = parser.next();

            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    curLevel++;
                    if (curLevel < skipLevel) {
                        qname = parser.getName();
                        prefix = qname.getPrefix();
                        name = (prefix.isEmpty() ?  "" : (prefix + ":"))
                                                         + qname.getLocalPart();
                        //name = parser.getLocalName();
                        if (current == null) {  // root document node
                            if (root.getName().compareTo(name) == 0) {
                                current = root;
                                if (current.hasAttribute()) {
                                    parseAttribute(current);
                                }
                                xpath.resetTreeVisited(current); // reset current children
                                current.setVisited(true);
                            } else {
                                createEmptyFields(root);
                                hasNext = false;
                            }
                        } else { // it is a child node
                            aux = current.getChild(name);
                            if (aux == null) { // no' que nao interessa
                                if (allowSubElems) {
                                    builder = current.getContent();
                                    if (builder == null) {
                                        builder = new StringBuilder();
                                        current.setContent(builder);
                                    }
                                    builder.append("<" + name + ">");
                                }
                                skipLevel = curLevel + 1;
                            } else {
                                current = aux;
                                xpath.resetTreeVisited(current); // reset current children
                                if (current.hasAttribute()) {
                                    parseAttribute(current);
                                }
                                current.setVisited(true);
                            }
                        }
                    } else if (allowSubElems) {
                        builder = current.getContent();
                        if (builder == null) {
                            builder = new StringBuilder();
                            current.setContent(builder);
                        }
                        builder.append("<" + parser.getLocalName() + ">");
                    }
                    break;

                /* Parser is not generating ATTRIBUTE event */
                /*case XMLStreamConstants.ATTRIBUTE:
                    curLevel++;
                    if (curLevel < skipLevel) {
                        parseAttribute(current);
                    }
                    curLevel--;
                    break;*/

                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.CHARACTERS:
                    curLevel++;
                    if (curLevel < skipLevel) {
                        if (current.getTag() != XPathTree.NULL_TAG) {
                            buffer = parser.getText();
                            if (!buffer.isEmpty()) {
                                builder = current.getContent();
                                if (builder == null) {
                                    builder = new StringBuilder();
                                    current.setContent(builder);
                                }
                                //builder.append(buffer);
                                builder.append(buffer.replace(
                                                     (char)REPLACE_CHAR, '&'));
                            }
                        }
                    } else if (allowSubElems) {
                        if (current.getTag() != XPathTree.NULL_TAG) {
                            buffer = parser.getText();
                            if (!buffer.isEmpty()) {
                                builder = current.getContent();
                                if (builder == null) {
                                    builder = new StringBuilder();
                                    current.setContent(builder);
                                }
                                builder.append(buffer.replace(
                                        (char) REPLACE_CHAR, '&'));
                            }
                        }
                    }
                    curLevel--;
                    break;

                case XMLStreamConstants.END_ELEMENT:
                    buffer = parser.getLocalName();
                    if (curLevel < skipLevel) {
                        if (skipLevel == Integer.MAX_VALUE) {
                            saveContent(current);
                            if (curLevel >= saveLevel) {
                                createEmptyFields(current);
                            }
                            if (current.isRecSave()) {
                                final String recContent = writer.getContent();
                                try {
                                    writer.saveRecord(
                                           createFileNameFld ? fileName : null);
                                } catch (BrumaException ze) {
                                    System.err.println(
                                   "WARNING: skipping record" +
                                   " database:" + writer.getDbName() +
                                   " fileName:" + fileName +
                                   " content:" + recContent.substring(0, 300) +
                                   " ...");
                                }
                            }
                            if (current == root) {
                                hasNext = false;
                            } else {
                                current = current.getFather();
                            }
                        } else {  // reset skipLevel
                            if (allowSubElems) {
                                builder = current.getContent();
                                if (builder == null) {
                                    builder = new StringBuilder();
                                    current.setContent(builder);
                                }
                                builder.append("</" + buffer + ">");
                            }
                            skipLevel = Integer.MAX_VALUE;
                        }
                    } else if (allowSubElems) {
                        builder = current.getContent();
                        if (builder == null) {
                            builder = new StringBuilder();
                            current.setContent(builder);
                        }
                        builder.append("</" + buffer + ">");
                    }
                    curLevel--;
                    break;

                default:
                    break;
            }
        }

        /*if (writer.hasFields()) {
            writer.saveRecord();
        }*/
        xpath.resetTreeVisited(root); // reset current children
    }

    private void createEmptyChildrenFields(
                final Map<String, XPathTree.TreeElement> children)
                                                        throws BrumaException {
        if (children != null) {
            for (XPathTree.TreeElement cur : children.values()) {
                createEmptyFields(cur);
            }
        }
    }

    private void createEmptyFields(final XPathTree.TreeElement current)
                                                        throws BrumaException {
        final int tag;

        if (createMissFld && (current != null)) {
            tag = current.getTag();
            if ((tag != XPathTree.NULL_TAG) &&  (!current.isVisited())) {
                writer.addField(tag, DEFAULT_EMPTY_FIELD);
            }
            current.setVisited(true);
            createEmptyChildrenFields(current.getChildren());
        }
    }

    private void saveContent(final XPathTree.TreeElement current)
                                                         throws BrumaException {
        final int tag;
        final StringBuilder builder;

        if (current != null) {
            tag = current.getTag();
            if (tag != XPathTree.NULL_TAG) {
                builder = current.getContent();
                if ((builder == null) && createMissFld) {
                    writer.addField(tag, "");
                } else if ((builder.length() > 0) || createMissFld) {
                    writer.addField(tag, builder.toString());
                    builder.setLength(0);
                }
            }
        }
    }

    private void parseAttribute(final XPathTree.TreeElement current)
                                                         throws BrumaException {
        assert current != null;

        final int attCount = parser.getAttributeCount();
        StringBuilder builder;
        int tag;
        String value;
        XPathTree.TreeElement elem;

        for (int index = 0; index < attCount; index++) {
            value = "@" + parser.getAttributeName(index);
            elem = current.getChild(value);
            if (elem != null) {
                tag = elem.getTag();
                builder = elem.getContent();
                if (builder == null) {
                    builder = new StringBuilder();
                } else {
                    builder.setLength(0);
                }
                //builder.append(parser.getAttributeValue(index));
                builder.append(parser.getAttributeValue(index)
                                            .replace((char)REPLACE_CHAR, '&'));
                elem.setContent(builder);
                writer.addField(tag, builder.toString());
                elem.setVisited(true);
            }
        }
    }
}

/**
 * XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES nao esta funcionando.
 * @author Heitor Barbieri
 */
class ReplaceBufferedReader extends BufferedReader {
    ReplaceBufferedReader(Reader in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int val = super.read();

        return val == '&' ? StaxXmlWalker.REPLACE_CHAR : val;
    }

    @Override
    public int read(char[] cbuf,
                    int off,
                    int len) throws IOException {
        int val = super.read(cbuf, off, len);
        for (int blen = 0; blen < len; blen++) {
            if (cbuf[off + blen] == '&') {
                cbuf[off + blen] = StaxXmlWalker.REPLACE_CHAR;
            }
        }
        return val;
    }
}
