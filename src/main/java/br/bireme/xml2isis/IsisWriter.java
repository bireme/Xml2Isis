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
import java.util.HashSet;

/**
 *
 * @author Heitor Barbieri
 */
public class IsisWriter {
    static final int DEFAULT_FILNAME_FIELD = 999;
    static final int DEFAULT_MAX_FIELD_LEN = 2048;
    static final int MEDLINE_MAX_FIELD_SIZE = 500;
    static final int MAX_FFI_RECORD_SIZE = 1048576;

    private final MasterFactory factory;
    private final String dbName;
    private final String encoding;
    private final Master master;
    private final Record record;
    private final HashSet<Integer> removableFields; // tags of fields that will be removed
                                              // if number of Fields > max (32k)
    private final int maxFldLength;
    private boolean tooManyFields;

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
        record = new Record();
        this.encoding = encoding;
        this.dbName = dbName;
        this.tooManyFields = false;
        this.removableFields = removableFields;
        this.maxFldLength = maxFldLength;
    }

    void close() throws BrumaException {
        if (master != null) {
            master.close();
        }
    }

    void newRecord() throws BrumaException {
        record.deleteFields();
        record.setMfn(0);
    }

    void addField(final int tag,
                  final String field) throws BrumaException {
        if (tag <= 0) {
            throw new IllegalArgumentException("tag <= 0");
        }
        final String fld = (field == null) ? "" : field;

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

        if (hasFields()) {
            if (tooManyFields) {
                record.deleteFields();
                record.setMfn(0);
                tooManyFields = false;
                throw new BrumaException("too many fields");
            } else {
                if (record.getRecordLength(encoding, true) >= MAX_FFI_RECORD_SIZE) {
                    trimFields();
                }
                if (record.getRecordLength(encoding, true) >= MAX_FFI_RECORD_SIZE) {
                  throw new BrumaException("record too big");
                }
                trimFields();
                master.writeRecord(record);
            }
        }
        record.deleteFields();
        record.setMfn(0);
    }

    private void trimFields() throws BrumaException {
      final Record rec = new Record();
      for (Field fld: record) {
        rec.addField(fld);
      }

      record.deleteFields();

      for (Field fld: rec) {
          final int id = fld.getId();
          String content = fld.getContent();

          if ((removableFields.contains(id)) && (content.length() > maxFldLength)) {
            content = content.substring(0, maxFldLength);
          }
          record.addField(id, content);
      }
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
