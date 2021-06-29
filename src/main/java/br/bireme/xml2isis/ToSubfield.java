/*=========================================================================

    Xml2Isis © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/Xml2Isis/blob/master/LICENSE.txt

  ==========================================================================*/

package br.bireme.xml2isis;

import bruma.BrumaException;
import bruma.master.Field;
import bruma.master.Master;
import bruma.master.MasterFactory;
import bruma.master.Record;
import java.util.ArrayList;
import java.util.List;

/**
 * Coloca todos os campos 'guestTag' depois do campo 'hostTag' no subcampo
 * 'subfield' do campo 'hostTag'.
 *
 * @author Heitor Barbieri
 * date 19/09/2010
 */
public class ToSubfield {
    public static void convert(final String dbName,
                               final String encoding,
                               final int hostTag,
                               final int guestTag,
                               final char subfield,
                               final boolean guestBeforeHost)
                                                        throws BrumaException {
        final Master mst = MasterFactory.getInstance(dbName).setEncoding(encoding)
                                                            .open();
        Record other;

        for (Record rec : mst) {
            if (rec.getStatus() == Record.Status.ACTIVE) {
                other = insertFields(rec, hostTag, guestTag, subfield,
                                                               guestBeforeHost);
                if (other != rec) {
                    mst.writeRecord(other);
                }
            }
        }

        mst.close();
    }

    private static Record insertFields(final Record rec,
                                      final int hostTag,
                                      final int guestTag,
                                      final char subfield,
                                      final boolean guestBeforeHost)
                                                         throws BrumaException {
        boolean found = false;
        final Record ret = new Record();
        final List<Field> flds = new ArrayList<Field>();
        final StringBuilder sb = new StringBuilder();
        int id;
        Field lastHost = null;

        ret.setMfn(rec.getMfn());

        for (Field fld : rec.getFields()) {
            id = fld.getId();
            if (id == hostTag) {
                found = true;
                if (guestBeforeHost) {
                    sb.setLength(0);
                    sb.append(fld.getContent());
                    for (Field guest : flds) {
                        sb.append("^");
                        sb.append(subfield);
                        sb.append(guest.getContent());
                    }
                    ret.addField(hostTag, sb.toString());
                    sb.setLength(0);
                } else { // Host before guest.
                    if (lastHost != null) {
                        sb.append(lastHost.getContent());
                    }
                    for (Field guest : flds) {
                        sb.append("^");
                        sb.append(subfield);
                        sb.append(guest.getContent());
                    }
                    if (sb.length() > 0) {
                        ret.addField(hostTag, sb.toString());
                        sb.setLength(0);
                    }
                    lastHost = fld;
                }
                flds.clear();
            } else if (id == guestTag) {
                found = true;
                flds.add(fld);
            } else {
                ret.addField(fld);
            }
        }
        if (lastHost != null) {
            sb.append(lastHost.getContent());
        }
        if (flds.size() > 0) {
            for (Field guest : flds) {
                sb.append("^");
                sb.append(subfield);
                sb.append(guest.getContent());
            }
        }
        if (sb.length() > 0) {
            ret.addField(hostTag, sb.toString());
        }

        return found ? ret : rec;
    }

    private static void usage() {
        System.err.println("usage: ToSubField <dbname> <encoding> <hostTag> "
                         + "<guestTag> <subfieldId> [--guestBeforeHost]");
        System.exit(1);
    }

    public static void main(final String[] args) throws BrumaException {
        if (args.length < 5) {
            usage();
        }
        final boolean before = (args.length > 5)
                                        && args[5].equals("--guestBeforeHost");

        convert(args[0], args[1], Integer.parseInt(args[2]),
               Integer.parseInt(args[3]), args[4].charAt(0), before);
    }
}
