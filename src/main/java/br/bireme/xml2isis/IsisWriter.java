package br.bireme.xml2isis;

import bruma.BrumaException;
import bruma.master.Field;
import bruma.master.Master;
import bruma.master.MasterFactory;
import bruma.master.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/**
 *
 * @author Heitor Barbieri
 */
public class IsisWriter {
    static final int DEFAULT_FILNAME_FIELD = 999;
    static final int DEFAULT_MAX_FIELD_LEN = 2048;
    static final int MEDLINE_MAX_FIELD_SIZE = 500;

    private final MasterFactory factory;
    private final String dbName;
    private Master master;
    private Record record;
    private boolean tooManyFields;
    private HashSet<Integer> removableFields; // tags of fields that will be removed
                                              // if number of Fields > max (32k)
    private int maxFldLength;

    IsisWriter(final String dbName,
               final String encoding) throws BrumaException {
        this(dbName, encoding, new HashSet<Integer>(), DEFAULT_MAX_FIELD_LEN);
    }

    IsisWriter(final String dbName,
               final String encoding,
               final HashSet<Integer> removableFields,
               final int maxFldLength) throws BrumaException {
        if (dbName == null) {
            throw new IllegalArgumentException();
        }
        factory = MasterFactory.getInstance(dbName)
                               .setInMemoryXrf(false)
                               .setFFI(true)
                               .setMaxGigaSize(32);
        if (encoding != null) {
            factory.setEncoding(encoding);
        }
        master = (Master)factory.create();
        record = null;
        this.dbName = dbName;
        this.tooManyFields = false;
        this.removableFields = removableFields;
        this.maxFldLength = maxFldLength;
    }

    void close() throws BrumaException {
        if (master != null) {
            master.close();
            master = null;
        }
    }

    void newRecord() throws BrumaException {
        if (record == null) {
            record = new Record();
        }
    }

    void addField(final int tag,
                  final String field) throws BrumaException {
        if (tag <= 0) {
            throw new IllegalArgumentException("tag <= 0");
        }
        String fld = (field == null) ? "" :
          field.substring(0, Math.min(maxFldLength, field.length()));

        if (record.getNvf() < 32768) { // max allowed number of fields
            record.addField(tag, fld);
        } else if (removableFields.isEmpty()) {
            tooManyFields = true;
        } else {  // Try deleting fields (removableFields)
            for (int rtag: removableFields) {
                deleteFields(rtag);
            }
            if (record.getNvf() < 32768) {
                record.addField(tag, fld);
                tooManyFields = false;
            } else {
              tooManyFields = true;
            }
        }
    }

    void deleteFields(final int tag)  throws BrumaException {
        if (tag <= 0) {
            throw new IllegalArgumentException("tag <= 0");
        }
        final List<Field> fields = record.getFields();
        final List<Field> remFields = new ArrayList<>();

        for (Field fld: fields) {
            if (fld.getId() == tag) remFields.add(fld);
        }
        for (Field fld: remFields) {
            fields.remove(fld);
        }
    }

    void saveRecord(final String fileName) throws BrumaException {
        if (master == null) {
            throw new IllegalArgumentException("null master");
        }
        if (record == null) {
            throw new IllegalArgumentException("null field");
        }
        if (fileName != null) {
            addField(DEFAULT_FILNAME_FIELD, fileName);
        }

/*System.out.println("-----------------------------------------------------------");
System.out.println(record);
System.out.println("-----------------------------------------------------------");*/
        if (hasFields()) {
            if (tooManyFields) {
                record.deleteFields();
                record.setMfn(0);
                tooManyFields = false;
                throw new BrumaException("too many fields");
            } else {
                master.writeRecord(record);
            }
        }
        record.deleteFields();
        record.setMfn(0);
    }

    boolean hasFields() {
        return (record == null) ? false : (record.getNvf() > 0);
    }

    String getDbName() {
      return dbName;
    }

    String getContent() {
      return record.toString();
    }
}
