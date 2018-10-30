/*=========================================================================

    Xml2Isis © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/Xml2Isis/blob/master/LICENSE.txt

  ==========================================================================*/
  
package br.bireme.xml2isis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Heitor Barbieri
 */
public class XPathTree {
    /** tag de no´ da arvore cujo conteudo nao sera usado. */
    static final int NULL_TAG = -1;

    class TreeElement {
        private final String name; // se comecar por @ e´ atributo
        private final TreeElement father;
        private int tag;
        private boolean visited;  // se este elem encontrou correspondente no arq XML
        private boolean recSave;  // should save the record?
        private boolean attribute; // is element and has attribute(s)
        private Map<String, TreeElement> children;
        private StringBuilder content; // conteudo do xml associado com o elemento

        TreeElement(final String name,
                    final int tag,
                    final TreeElement father) {
            assert name != null;

            this.name = name;
            this.father = father;
            this.tag = tag;
            this.visited = false;
            this.recSave = false;
            this.attribute = false;
            this.children = null;
            this.content = null;

            if ((name.charAt(0) == '@')
                                    && (tag == XPathTree.NULL_TAG)) {
                throw new IllegalArgumentException("invalid attribute tag");
            }
        }

        void addChild(final TreeElement elem) {
            assert elem != null;

            if (children == null) {
                children = new LinkedHashMap < String, TreeElement >();
            }
            children.put(elem.getName(), elem);
        }

        TreeElement getChild(final String name) {
            return ((children == null) || (name == null))
                                                 ? null : children.get(name);
        }

        Map<String,TreeElement> getChildren() {
            return children;
        }

        String getName() {
            return name;
        }

        int getTag() {
            return tag;
        }

        void setTag(final int nTag) {
            this.tag = nTag;
        }

        boolean isVisited() {
            return visited;
        }

        void setVisited(final boolean visited) {
            this.visited = visited;
        }

        boolean isRecSave() {
            return recSave;
        }

        void setRecSave(final boolean opt) {
            recSave = opt;
        }

        boolean hasAttribute() {
            return attribute;
        }

        void setHasAttribute(final boolean opt) {
            this.attribute = opt;
        }

        TreeElement getFather() {
            return father;
        }

        StringBuilder getContent() {
            return content;
        }

        void setContent(final StringBuilder content) {
            this.content = content;
        }

        String toString(final int spaces) {
            final StringBuilder builder = new StringBuilder();

            for (int counter = 0; counter < spaces; counter++) {
                builder.append(" ");
            }
            builder.append(name);
            if (isRecSave()) {
                builder.append(" *");
            }

            return builder.toString();
        }
    }

    private TreeElement root;
    private int saveLevel;

    XPathTree(final File xpath2Isis) throws IOException {
        if (xpath2Isis == null) {
            throw new IllegalArgumentException();
        }
        root = null;
        parseFile(xpath2Isis);
        saveLevel = setSaveLevel(root, 1);
    }

    TreeElement getRoot() {
        return root;
    }

    int getSaveLevel() {
        return saveLevel;
    }

    private void parseFile(final File xpath2Isis) throws IOException {
        assert xpath2Isis != null;

        final BufferedReader reader =
                              new BufferedReader(new FileReader(xpath2Isis));
        final Pattern pattern = Pattern.compile(
                      "(\\d+)\\s+((/[\\w\\-\\.:]+)+(@[\\w\\-\\.]+)?)");
        String line = null;
        Matcher mat;

        resetTreeVisited(root);
        while (true) {
            line = reader.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (!line.isEmpty()) {
                if (line.charAt(0) != '#') {
                    mat = pattern.matcher(line);
                    if (mat.matches()) {
                       // Tira / inicial do xpath (/ final nao existe - reg exp)
                        putElement(mat.group(1), mat.group(2).substring(1));
                    } else {
                        throw new IOException(
                                      "unsuported xpath specification:" + line);
                    }
                }
            }
        }
        setRecSave(root);
        reader.close();
//System.out.println(toString());
    }

    /**
     * Set the RecSave flag of the lower commom element of all path expressions
     * @param current root element of a subtree.
     */
    private void setRecSave(final TreeElement current) {
        if (current != null) {
            final Map<String, TreeElement> children = current.getChildren();
            final int num = (children == null) ? 0 : children.size();

            if (num == 0) {  // Não tem filho, então salva toda vez que chegar a este elemento
                current.setRecSave(true);
            } else if (num == 1) { // Tem um só filho
                final TreeElement child = children.values().iterator().next();
                final Map<String, TreeElement> grandChildren = child.getChildren();
                final int num2 = (grandChildren == null) ? 0 : grandChildren.size();

                if (num2 == 0) { // Se filho não tem descendentes então salva toda vez que chegar a este elemento
                    current.setRecSave(true);
                } else { // Percorre recursivamente
                    setRecSave(children.values().iterator().next());
                }
            } else { // Tem mais de um filho, então salva toda vez que chegar a este elemento
                current.setRecSave(true);
            }
        }
    }

    private void putElement(final String stag,
                            final String xpath) throws IOException {
        assert stag != null;
        assert xpath != null;

        final int tag = Integer.parseInt(stag);
        final String[] split = xpath.split("/");
        final int len = split.length;
        int pos = split[0].indexOf('@');
        Map<String, TreeElement> children;
        TreeElement current;
        TreeElement child;
        String eName;
        boolean found;

        if ((tag != NULL_TAG) && (tag < 0)) {
            throw new IOException("invalid tag:" + stag);
        }
        if (root == null) {
            final int etag = len > 1 ? NULL_TAG : tag;

            if (pos == -1) { // Nao tem @ - nao tem atributo
                root = new TreeElement(split[0], etag, null);
            } else {
                root = new TreeElement(split[0].substring(0, pos), etag, null);
            }
        } else if (root.getName().compareTo(split[0]) != 0) {
            throw new IllegalArgumentException("only one root element allowed");
        }
        current = root;

        // Trata nao folhas do xpath
        for (int index = 1; index < len - 1; index++) {
            eName = split[index];
            if (eName.contains("@")) {
                throw new IllegalArgumentException(
                                              "atttribute is not at the leaf");
            }
            found = false;
            children = current.getChildren();

            if (children != null) {
                for (TreeElement ch : children.values()) {
                    if (ch.getName().compareTo(eName) == 0) {
                        current = ch;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                child = new TreeElement(eName, NULL_TAG, current);
                current.addChild(child);
                current = child;
            }
        }

        // Trata a folha do xpath
        if (len == 1) {  // xpath so tem um nivel
            if (pos == -1) { // Nao tem atributo
                if (current.getTag() == NULL_TAG) {
                    current.setTag(tag);
                } else if (current.getTag() != tag) {
                    throw new IllegalArgumentException("different leaf tag");
                }
            } else { // Tem atributo
                final String attr = split[0].substring(pos);

                child = current.getChild(attr);
                if (child != null) {
                    throw new IllegalArgumentException("duplicated attribute:"
                                             + attr);
                }
                current.addChild(new TreeElement(attr, tag, current));
                current.setHasAttribute(true);
            }
        } else { // xpath tem mais de um nivel
            pos = split[len - 1].indexOf('@');

            if (pos == -1) { // Nao tem atributo
                eName = split[len - 1];
                child = current.getChild(eName);

                if (child == null) {
                    child = new TreeElement(eName, tag, current);
                    current.addChild(child);
                } else {
                    final int ctag = child.getTag();
                    if (ctag == NULL_TAG) {
                        child.setTag(tag);
                    } else if (ctag != tag) {
                        throw new IllegalArgumentException(
                                                          "different leaf tag");
                    }
                }
                current = child;
            } else { // Tem atributo
                final String attr = split[len - 1].substring(pos);

                eName = split[len - 1].substring(0, pos);
                child = current.getChild(eName);
                if (child == null) {
                    child = new TreeElement(eName, NULL_TAG, current);
                    current.addChild(child);
                }
                current = child;
                child = current.getChild(attr);
                if (child != null) {
                    throw new IllegalArgumentException("duplicated attribute:"
                                                 + attr);
                }
                current.addChild(new TreeElement(attr, tag, current));
                current.setHasAttribute(true);
            }
        }
//System.out.println(toString());
    }

    /**
     * @return nivel da arvore onde esta marcado o elemento que contem
     *  recSave = true. O nivel do root e' 1.
     */
    private int setSaveLevel(final TreeElement current,
                            final int curLevel) {
        assert curLevel > 0;

        int ret = 0;

        if (current != null) {
            if (current.isRecSave()) {
                ret = curLevel;
            } else {
                final Map<String,TreeElement> children = current.getChildren();

                if (children != null) {
                    for (TreeElement child : children.values()) {
                        ret = setSaveLevel(child, curLevel + 1);
                        if (ret > 0) {
                            break;
                        }
                    }
                }
            }
        }

        return ret;
    }

    void resetTreeVisited(final TreeElement current) {
        final Map<String, TreeElement> children;

        if (current != null) {
            current.setVisited(false);
            children = current.getChildren();
            if (children != null) {
                for (TreeElement child : children.values()) {
                    resetTreeVisited(child);
                }
            }
        }
    }

    void toString(final TreeElement elem,
                  final int spaces,
                  final StringBuilder builder) {
        assert builder != null;
        assert spaces >= 0;

        final Map<String, TreeElement> ch1;

        if (elem != null) {
            builder.append(elem.toString(spaces));
            builder.append("\n");
            ch1 = elem.getChildren();
            if (ch1 != null) {
                for (TreeElement child : ch1.values()) {
                    toString(child, spaces + 2, builder);
                }
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        toString(root, 0, builder);

        return builder.toString();
    }
}
