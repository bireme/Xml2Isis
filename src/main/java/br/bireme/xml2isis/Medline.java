package br.bireme.xml2isis;

import bruma.BrumaException;
import bruma.master.Field;
import bruma.master.Master;
import bruma.master.MasterFactory;
import bruma.master.Record;
import java.util.Iterator;

/**
 *
 * @author Heitor Barbieri
 * @date 20/09/2010
 */
public class Medline {
    public static final int DESCRIPTOR_NAME_MTYN = 3511;
    public static final int DESCRIPTOR_NAME = 3512;
    public static final int QUALIFIER_NAME_MTYN = 3513;
    public static final int QUALIFIER_NAME = 3514;
    public static final int OUTPUT_TAG = 351;

    public static void convert(final String inDb,
                               final String encoding,
                               final String outDb) throws BrumaException {
        if (inDb == null) {
            throw new BrumaException("null inDb");
        }
        if (encoding == null) {
            throw new BrumaException("null encoding");
        }
        if (outDb == null) {
            throw new BrumaException("null outDb");
        }
        final Master inMst = MasterFactory.getInstance(inDb)
                                          .setEncoding(encoding)
                                          .open();
        final Master outMst = (Master)MasterFactory.getInstance(outDb)
                                          .asAnotherMaster(inMst)
                                          .create();
        for (Record rec : inMst) {
            try {
                saveRecord(parseRecord(rec), outMst);
            } catch (BrumaException ze) {
              System.err.println("WARNING: skipping record mfn:" + rec.getMfn() +
              " database:" + inDb);
            }
        }
        inMst.close();
        outMst.close();
    }

    private static Record parseRecord(final Record rec) throws BrumaException {
        if (rec.getStatus() != Record.Status.ACTIVE) {
            throw new BrumaException("Record is not in active status");
        }

        final Record outrec = new Record();
        final Iterator<Field> it = rec.iterator();
        Field fld;
        String prefix;
        String sufix;
        String content;
        int fid;

        if (it.hasNext()) {
            fld = it.next();

            do {
                fid = fld.getId();
                if ((fid != DESCRIPTOR_NAME_MTYN) &&
                    (fid != DESCRIPTOR_NAME) &&
                    (fid != QUALIFIER_NAME_MTYN) &&
                    (fid != QUALIFIER_NAME)) {
                    outrec.addField(fld.getId(), fld.getContent());
                    if (!it.hasNext()) {
                        break;
                    }
                    fld = it.next();
                    continue;
                }
                sufix = null;
                if (fld.getId() == DESCRIPTOR_NAME_MTYN) {
                    content = fld.getContent();
                    if (content.isEmpty()) {
                        prefix = "";
                    } else {
                        prefix = "^a" + content;
                    }
                    if (!it.hasNext()) {
                        throw new BrumaException("DescriptorName required");
                    }
                    fld = it.next();
                    if (fld.getId() != DESCRIPTOR_NAME) {
                        throw new BrumaException("DescriptorName required " +
                                                "[id=" + fld.getId() + "]");
                    }
                    content = fld.getContent();
                    if (content.isEmpty()) {
                        if (!prefix.isEmpty()) {
                            throw new BrumaException(
                                            "DescriptorName attribute required "
                                                + " [id=" + fld.getId() + "]");
                        }
                    } else {
                        if (prefix.isEmpty()) {
                            throw new BrumaException(
                                               "DescriptorName required "
                                                + " [id=" + fld.getId() + "]");
                        }
                    }
                    prefix = content + prefix;
                } else if (fld.getId() == DESCRIPTOR_NAME) {
                    content = fld.getContent();
                    if (content.isEmpty()) {
                        prefix = "";
                    } else {
                        prefix = content;
                    }
                    if (!it.hasNext()) {
                        throw new BrumaException("DescriptorName atttribute required"
                                               + " [id=" + fld.getId() + "]");
                    }
                    fld = it.next();
                    if (fld.getId() != DESCRIPTOR_NAME_MTYN) {
                        throw new BrumaException("DescriptorName atttribute required"
                                                + " [id=" + fld.getId() + "]");
                    }
                    content = fld.getContent();
                    if (content.isEmpty()) {
                        if (!prefix.isEmpty()) {
                            throw new BrumaException(
                                               "DescriptorName attribute required "
                                                + " [id=" + fld.getId() + "]");
                        }
                    } else {
                        if (prefix.isEmpty()) {
                            throw new BrumaException(
                                               "DescriptorName required "
                                                + " [id=" + fld.getId() + "]");
                        }
                    }
                    prefix = prefix + "^a" + content;
                } else {
                    throw new BrumaException("DescriptorName [attribute] required");
                }
                while (it.hasNext()) {
                    fld = it.next();
                    if (fld.getId() == DESCRIPTOR_NAME_MTYN) {
                        break;
                    }
                    if (fld.getId() == QUALIFIER_NAME_MTYN) {
                        content = fld.getContent();
                        if (content.isEmpty()) {
                            sufix = "";
                        } else {
                            sufix = "^b" + content;
                        }
                        if (!it.hasNext()) {
                            throw new BrumaException("QualifierName required " +
                                                    "[id=" + fld.getId() + "]");
                        }
                        fld = it.next();
                        if (fld.getId() != QUALIFIER_NAME) {
                            throw new BrumaException("QualifierName required " +
                                                    "[id=" + fld.getId() + "]");
                        }
                        content = fld.getContent();
                        if (!content.isEmpty()) {
                            content = "^q" + content;
                        }
                        outrec.addField(OUTPUT_TAG, prefix + content + sufix);
                    } else if (fld.getId() == QUALIFIER_NAME) {
                        content = fld.getContent();
                        if (content.isEmpty()) {
                            sufix = "";
                        } else {
                            sufix = "^q" + content;
                        }
                        if (!it.hasNext()) {
                            throw new BrumaException(
                                            "QualifierName attribute required");
                        }
                        fld = it.next();
                        if (fld.getId() != QUALIFIER_NAME_MTYN) {
                            throw new BrumaException(
                                            "QualifierName attribute required" +
                                            "[id=" + fld.getId() + "]");
                        }
                        content = fld.getContent();
                        if (!content.isEmpty()) {
                            content = "^b" + content;
                        }
                        outrec.addField(OUTPUT_TAG, prefix + sufix + content);
                    } else {
                        outrec.addField(fld.getId(), fld.getContent());
                    }
    //System.out.println("adicionei campo");
                }
                if (sufix == null) {
                    outrec.addField(OUTPUT_TAG, prefix);
                }
            } while (it.hasNext());
        }

        return outrec;
    }

    private static void saveRecord(final Record rec,
                                   final Master out) throws BrumaException {
      if ((rec != null) && (rec.getNvf() > 0)) {
          out.writeRecord(rec);
      }
    }

    private static void usage() {
        System.err.println("usage: Medline <indb> <encoding> <outDb>");
        System.exit(1);
    }

    public static void main(final String[] args) throws BrumaException {
        if (args.length != 3) {
            usage();
        }
        convert(args[0], args[1], args[2]);
    }
}
