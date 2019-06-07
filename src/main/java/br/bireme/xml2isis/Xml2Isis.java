/*=========================================================================

    Xml2Isis © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/Xml2Isis/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.xml2isis;

import br.bireme.utils.TimeString;
import bruma.BrumaException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamException;

/**
 *
 * @author Heitor Barbieri
 */
public class Xml2Isis {
    private static class MyFileFilter implements FileFilter {
        private final Pattern pat;

        private MyFileFilter(final String regExp) {
            assert regExp != null;
            pat = Pattern.compile(regExp);
        }
        public boolean accept(File pathname)  {
            return (pathname.isFile()
                    && pat.matcher(pathname.getName()).matches());
        }
    }

    private Xml2Isis() { }

    private static void usage() {
        System.err.println("Application to import documents from xml files into Isis database records.\n");
        System.err.println("usage: Xml2Isis fileDir=<directory> => xml file directory\n"
                         +  "                xmlRegExp=<expression> => regular expression used to filter the input xml files\n"
                         +  "                convTable=<file> => file having the convertion from xml elements into isis record fields\n"
                         +  "                outDb=<name> => output Isis database\n"
                         +  "                [--createMissingFields] => create an empty field if the xml element was not found\n"
                         +  "                [--createFileNameField] => create a record field with the name of the file from where the document comes\n"
                         +  "                [--allowSubElements] => include xml subelements as part of the element text content\n"
                         +  "                [fileEncoding=<encoding>] => the encoding of the xml files \n"
                         +  "                [dbEncoding=<encoding>] => the encoding of the output database\n"
                         +  "                [tell=<number>] => prints an message each <number> documents processed\n"
                         +  "                [removableFieldTags=<tag1>,<tag2>,...,<tagN>]  => delete those fields if there are too many fields\n"
                         +  "                [maxFieldLength=<len>] => limit the size of removableFieldTags if record size is too big");
        System.exit(1);
    }

    public static void main(final String[] args) throws IOException,
                                                        XMLStreamException,
                                                        BrumaException {
        if (args.length < 4) {
            usage();
        }

        final File directory;
        final File[] files;
        final XPathTree tree;
        final IsisWriter writer;
        final TimeString time = new TimeString();

        String dir = null;
        String regExp = null;
        String table = null;
        String outDb = null;
        String fileEncoding = null;
        String dbEncoding = null;
        boolean createMissFld = false;
        boolean createFilNameFld = false;
        boolean allowSubElements = false;
        String parm;
        StaxXmlWalker walker;
        int tell = 1;
        HashSet<Integer> removableFieldTags = new HashSet<>();
        int maxFieldLength = IsisWriter.MEDLINE_MAX_FIELD_SIZE;
        int cur = 1;

        for (int counter = 0; counter < args.length; counter++) {
            parm = args[counter];
            if (parm.startsWith("fileDir=")) {
                dir = parm.substring(8);
            } else if (parm.startsWith("xmlRegExp=")) {
                regExp = parm.substring(10);
            } else if (parm.startsWith("convTable=")) {
                table = parm.substring(10);
            } else if (parm.startsWith("outDb=")) {
                outDb = parm.substring(6);
            } else if (parm.compareTo("--createMissingFields") == 0) {
                createMissFld = true;
            } else if (parm.compareTo("--createFileNameField") == 0) {
                createFilNameFld = true;
            } else if (parm.compareTo("--allowSubElements") == 0) {
                allowSubElements = true;
            } else if (parm.startsWith("fileEncoding=")) {
                fileEncoding = parm.substring(13);
            } else if (parm.startsWith("dbEncoding=")) {
                dbEncoding = parm.substring(11);
            } else if (parm.startsWith("tell=")) {
                tell = Integer.parseInt(parm.substring(5));
            } else if (parm.startsWith("removableFieldTags=")) {
                final String[] split = parm.substring(19).split(" *\\, *");
                for (String spl: split) {
                  removableFieldTags.add(Integer.parseInt(spl));
                }
            } else if (parm.startsWith("maxFieldLength=")) {
                maxFieldLength = Integer.parseInt(parm.substring(15));
            } else {
                usage();
            }
        }

        if ((dir == null) || (regExp == null)
                                       || (table == null) || (outDb == null)) {
            throw new IllegalArgumentException("missing parameter");
        }

        directory = new File(dir);
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(dir + " is not a directory");
        }

        files = directory.listFiles(new MyFileFilter(regExp));
        tree = new XPathTree(new File(table));
        writer = new IsisWriter(outDb, dbEncoding, removableFieldTags,
                                                                maxFieldLength);
        time.start();

        for (File curFile : files) {
            if ((cur % tell) == 0) {
                System.out.println("+++ " + cur + " : "
                   + curFile.getCanonicalPath() + " ("
                   + time.getTime() + ")");
            }
            cur++;
            walker = new StaxXmlWalker(curFile, tree, writer,
                                       createMissFld, allowSubElements, fileEncoding);
            walker.createFileNameField(createFilNameFld);
            walker.convert();
            walker.close();
        }

        writer.close();

        System.out.println("Total converted files: " + (cur - 1));
    }
}
