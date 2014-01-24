/**
 * Autogenerated by Thrift Compiler (0.9.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.fidonet.jftn.rpc;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaStatistics implements org.apache.thrift.TBase<JavaStatistics, JavaStatistics._Fields>, java.io.Serializable, Cloneable, Comparable<JavaStatistics> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("JavaStatistics");

  private static final org.apache.thrift.protocol.TField JAVA_VERSION_FIELD_DESC = new org.apache.thrift.protocol.TField("java_version", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField TOTAL_MEMMORY_FIELD_DESC = new org.apache.thrift.protocol.TField("total_memmory", org.apache.thrift.protocol.TType.I64, (short)2);
  private static final org.apache.thrift.protocol.TField USAGE_MEMMORY_FIELD_DESC = new org.apache.thrift.protocol.TField("usage_memmory", org.apache.thrift.protocol.TType.I64, (short)3);
  private static final org.apache.thrift.protocol.TField TOTAL_THREADS_FIELD_DESC = new org.apache.thrift.protocol.TField("total_threads", org.apache.thrift.protocol.TType.I16, (short)4);
  private static final org.apache.thrift.protocol.TField RUN_THREADS_FIELD_DESC = new org.apache.thrift.protocol.TField("run_threads", org.apache.thrift.protocol.TType.I16, (short)5);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new JavaStatisticsStandardSchemeFactory());
    schemes.put(TupleScheme.class, new JavaStatisticsTupleSchemeFactory());
  }

  public String java_version; // required
  public long total_memmory; // required
  public long usage_memmory; // required
  public short total_threads; // required
  public short run_threads; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    JAVA_VERSION((short)1, "java_version"),
    TOTAL_MEMMORY((short)2, "total_memmory"),
    USAGE_MEMMORY((short)3, "usage_memmory"),
    TOTAL_THREADS((short)4, "total_threads"),
    RUN_THREADS((short)5, "run_threads");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // JAVA_VERSION
          return JAVA_VERSION;
        case 2: // TOTAL_MEMMORY
          return TOTAL_MEMMORY;
        case 3: // USAGE_MEMMORY
          return USAGE_MEMMORY;
        case 4: // TOTAL_THREADS
          return TOTAL_THREADS;
        case 5: // RUN_THREADS
          return RUN_THREADS;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __TOTAL_MEMMORY_ISSET_ID = 0;
  private static final int __USAGE_MEMMORY_ISSET_ID = 1;
  private static final int __TOTAL_THREADS_ISSET_ID = 2;
  private static final int __RUN_THREADS_ISSET_ID = 3;
  private byte __isset_bitfield = 0;
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.JAVA_VERSION, new org.apache.thrift.meta_data.FieldMetaData("java_version", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.TOTAL_MEMMORY, new org.apache.thrift.meta_data.FieldMetaData("total_memmory", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.USAGE_MEMMORY, new org.apache.thrift.meta_data.FieldMetaData("usage_memmory", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.TOTAL_THREADS, new org.apache.thrift.meta_data.FieldMetaData("total_threads", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I16)));
    tmpMap.put(_Fields.RUN_THREADS, new org.apache.thrift.meta_data.FieldMetaData("run_threads", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I16)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(JavaStatistics.class, metaDataMap);
  }

  public JavaStatistics() {
  }

  public JavaStatistics(
    String java_version,
    long total_memmory,
    long usage_memmory,
    short total_threads,
    short run_threads)
  {
    this();
    this.java_version = java_version;
    this.total_memmory = total_memmory;
    setTotal_memmoryIsSet(true);
    this.usage_memmory = usage_memmory;
    setUsage_memmoryIsSet(true);
    this.total_threads = total_threads;
    setTotal_threadsIsSet(true);
    this.run_threads = run_threads;
    setRun_threadsIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public JavaStatistics(JavaStatistics other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetJava_version()) {
      this.java_version = other.java_version;
    }
    this.total_memmory = other.total_memmory;
    this.usage_memmory = other.usage_memmory;
    this.total_threads = other.total_threads;
    this.run_threads = other.run_threads;
  }

  public JavaStatistics deepCopy() {
    return new JavaStatistics(this);
  }

  @Override
  public void clear() {
    this.java_version = null;
    setTotal_memmoryIsSet(false);
    this.total_memmory = 0;
    setUsage_memmoryIsSet(false);
    this.usage_memmory = 0;
    setTotal_threadsIsSet(false);
    this.total_threads = 0;
    setRun_threadsIsSet(false);
    this.run_threads = 0;
  }

  public String getJava_version() {
    return this.java_version;
  }

  public JavaStatistics setJava_version(String java_version) {
    this.java_version = java_version;
    return this;
  }

  public void unsetJava_version() {
    this.java_version = null;
  }

  /** Returns true if field java_version is set (has been assigned a value) and false otherwise */
  public boolean isSetJava_version() {
    return this.java_version != null;
  }

  public void setJava_versionIsSet(boolean value) {
    if (!value) {
      this.java_version = null;
    }
  }

  public long getTotal_memmory() {
    return this.total_memmory;
  }

  public JavaStatistics setTotal_memmory(long total_memmory) {
    this.total_memmory = total_memmory;
    setTotal_memmoryIsSet(true);
    return this;
  }

  public void unsetTotal_memmory() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __TOTAL_MEMMORY_ISSET_ID);
  }

  /** Returns true if field total_memmory is set (has been assigned a value) and false otherwise */
  public boolean isSetTotal_memmory() {
    return EncodingUtils.testBit(__isset_bitfield, __TOTAL_MEMMORY_ISSET_ID);
  }

  public void setTotal_memmoryIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __TOTAL_MEMMORY_ISSET_ID, value);
  }

  public long getUsage_memmory() {
    return this.usage_memmory;
  }

  public JavaStatistics setUsage_memmory(long usage_memmory) {
    this.usage_memmory = usage_memmory;
    setUsage_memmoryIsSet(true);
    return this;
  }

  public void unsetUsage_memmory() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __USAGE_MEMMORY_ISSET_ID);
  }

  /** Returns true if field usage_memmory is set (has been assigned a value) and false otherwise */
  public boolean isSetUsage_memmory() {
    return EncodingUtils.testBit(__isset_bitfield, __USAGE_MEMMORY_ISSET_ID);
  }

  public void setUsage_memmoryIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __USAGE_MEMMORY_ISSET_ID, value);
  }

  public short getTotal_threads() {
    return this.total_threads;
  }

  public JavaStatistics setTotal_threads(short total_threads) {
    this.total_threads = total_threads;
    setTotal_threadsIsSet(true);
    return this;
  }

  public void unsetTotal_threads() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __TOTAL_THREADS_ISSET_ID);
  }

  /** Returns true if field total_threads is set (has been assigned a value) and false otherwise */
  public boolean isSetTotal_threads() {
    return EncodingUtils.testBit(__isset_bitfield, __TOTAL_THREADS_ISSET_ID);
  }

  public void setTotal_threadsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __TOTAL_THREADS_ISSET_ID, value);
  }

  public short getRun_threads() {
    return this.run_threads;
  }

  public JavaStatistics setRun_threads(short run_threads) {
    this.run_threads = run_threads;
    setRun_threadsIsSet(true);
    return this;
  }

  public void unsetRun_threads() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __RUN_THREADS_ISSET_ID);
  }

  /** Returns true if field run_threads is set (has been assigned a value) and false otherwise */
  public boolean isSetRun_threads() {
    return EncodingUtils.testBit(__isset_bitfield, __RUN_THREADS_ISSET_ID);
  }

  public void setRun_threadsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __RUN_THREADS_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case JAVA_VERSION:
      if (value == null) {
        unsetJava_version();
      } else {
        setJava_version((String)value);
      }
      break;

    case TOTAL_MEMMORY:
      if (value == null) {
        unsetTotal_memmory();
      } else {
        setTotal_memmory((Long)value);
      }
      break;

    case USAGE_MEMMORY:
      if (value == null) {
        unsetUsage_memmory();
      } else {
        setUsage_memmory((Long)value);
      }
      break;

    case TOTAL_THREADS:
      if (value == null) {
        unsetTotal_threads();
      } else {
        setTotal_threads((Short)value);
      }
      break;

    case RUN_THREADS:
      if (value == null) {
        unsetRun_threads();
      } else {
        setRun_threads((Short)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case JAVA_VERSION:
      return getJava_version();

    case TOTAL_MEMMORY:
      return Long.valueOf(getTotal_memmory());

    case USAGE_MEMMORY:
      return Long.valueOf(getUsage_memmory());

    case TOTAL_THREADS:
      return Short.valueOf(getTotal_threads());

    case RUN_THREADS:
      return Short.valueOf(getRun_threads());

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case JAVA_VERSION:
      return isSetJava_version();
    case TOTAL_MEMMORY:
      return isSetTotal_memmory();
    case USAGE_MEMMORY:
      return isSetUsage_memmory();
    case TOTAL_THREADS:
      return isSetTotal_threads();
    case RUN_THREADS:
      return isSetRun_threads();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof JavaStatistics)
      return this.equals((JavaStatistics)that);
    return false;
  }

  public boolean equals(JavaStatistics that) {
    if (that == null)
      return false;

    boolean this_present_java_version = true && this.isSetJava_version();
    boolean that_present_java_version = true && that.isSetJava_version();
    if (this_present_java_version || that_present_java_version) {
      if (!(this_present_java_version && that_present_java_version))
        return false;
      if (!this.java_version.equals(that.java_version))
        return false;
    }

    boolean this_present_total_memmory = true;
    boolean that_present_total_memmory = true;
    if (this_present_total_memmory || that_present_total_memmory) {
      if (!(this_present_total_memmory && that_present_total_memmory))
        return false;
      if (this.total_memmory != that.total_memmory)
        return false;
    }

    boolean this_present_usage_memmory = true;
    boolean that_present_usage_memmory = true;
    if (this_present_usage_memmory || that_present_usage_memmory) {
      if (!(this_present_usage_memmory && that_present_usage_memmory))
        return false;
      if (this.usage_memmory != that.usage_memmory)
        return false;
    }

    boolean this_present_total_threads = true;
    boolean that_present_total_threads = true;
    if (this_present_total_threads || that_present_total_threads) {
      if (!(this_present_total_threads && that_present_total_threads))
        return false;
      if (this.total_threads != that.total_threads)
        return false;
    }

    boolean this_present_run_threads = true;
    boolean that_present_run_threads = true;
    if (this_present_run_threads || that_present_run_threads) {
      if (!(this_present_run_threads && that_present_run_threads))
        return false;
      if (this.run_threads != that.run_threads)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder();

    boolean present_java_version = true && (isSetJava_version());
    builder.append(present_java_version);
    if (present_java_version)
      builder.append(java_version);

    boolean present_total_memmory = true;
    builder.append(present_total_memmory);
    if (present_total_memmory)
      builder.append(total_memmory);

    boolean present_usage_memmory = true;
    builder.append(present_usage_memmory);
    if (present_usage_memmory)
      builder.append(usage_memmory);

    boolean present_total_threads = true;
    builder.append(present_total_threads);
    if (present_total_threads)
      builder.append(total_threads);

    boolean present_run_threads = true;
    builder.append(present_run_threads);
    if (present_run_threads)
      builder.append(run_threads);

    return builder.toHashCode();
  }

  @Override
  public int compareTo(JavaStatistics other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetJava_version()).compareTo(other.isSetJava_version());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetJava_version()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.java_version, other.java_version);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetTotal_memmory()).compareTo(other.isSetTotal_memmory());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTotal_memmory()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.total_memmory, other.total_memmory);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetUsage_memmory()).compareTo(other.isSetUsage_memmory());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetUsage_memmory()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.usage_memmory, other.usage_memmory);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetTotal_threads()).compareTo(other.isSetTotal_threads());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTotal_threads()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.total_threads, other.total_threads);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetRun_threads()).compareTo(other.isSetRun_threads());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetRun_threads()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.run_threads, other.run_threads);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("JavaStatistics(");
    boolean first = true;

    sb.append("java_version:");
    if (this.java_version == null) {
      sb.append("null");
    } else {
      sb.append(this.java_version);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("total_memmory:");
    sb.append(this.total_memmory);
    first = false;
    if (!first) sb.append(", ");
    sb.append("usage_memmory:");
    sb.append(this.usage_memmory);
    first = false;
    if (!first) sb.append(", ");
    sb.append("total_threads:");
    sb.append(this.total_threads);
    first = false;
    if (!first) sb.append(", ");
    sb.append("run_threads:");
    sb.append(this.run_threads);
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class JavaStatisticsStandardSchemeFactory implements SchemeFactory {
    public JavaStatisticsStandardScheme getScheme() {
      return new JavaStatisticsStandardScheme();
    }
  }

  private static class JavaStatisticsStandardScheme extends StandardScheme<JavaStatistics> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, JavaStatistics struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // JAVA_VERSION
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.java_version = iprot.readString();
              struct.setJava_versionIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // TOTAL_MEMMORY
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.total_memmory = iprot.readI64();
              struct.setTotal_memmoryIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // USAGE_MEMMORY
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.usage_memmory = iprot.readI64();
              struct.setUsage_memmoryIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // TOTAL_THREADS
            if (schemeField.type == org.apache.thrift.protocol.TType.I16) {
              struct.total_threads = iprot.readI16();
              struct.setTotal_threadsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // RUN_THREADS
            if (schemeField.type == org.apache.thrift.protocol.TType.I16) {
              struct.run_threads = iprot.readI16();
              struct.setRun_threadsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, JavaStatistics struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.java_version != null) {
        oprot.writeFieldBegin(JAVA_VERSION_FIELD_DESC);
        oprot.writeString(struct.java_version);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(TOTAL_MEMMORY_FIELD_DESC);
      oprot.writeI64(struct.total_memmory);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(USAGE_MEMMORY_FIELD_DESC);
      oprot.writeI64(struct.usage_memmory);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(TOTAL_THREADS_FIELD_DESC);
      oprot.writeI16(struct.total_threads);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(RUN_THREADS_FIELD_DESC);
      oprot.writeI16(struct.run_threads);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class JavaStatisticsTupleSchemeFactory implements SchemeFactory {
    public JavaStatisticsTupleScheme getScheme() {
      return new JavaStatisticsTupleScheme();
    }
  }

  private static class JavaStatisticsTupleScheme extends TupleScheme<JavaStatistics> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, JavaStatistics struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetJava_version()) {
        optionals.set(0);
      }
      if (struct.isSetTotal_memmory()) {
        optionals.set(1);
      }
      if (struct.isSetUsage_memmory()) {
        optionals.set(2);
      }
      if (struct.isSetTotal_threads()) {
        optionals.set(3);
      }
      if (struct.isSetRun_threads()) {
        optionals.set(4);
      }
      oprot.writeBitSet(optionals, 5);
      if (struct.isSetJava_version()) {
        oprot.writeString(struct.java_version);
      }
      if (struct.isSetTotal_memmory()) {
        oprot.writeI64(struct.total_memmory);
      }
      if (struct.isSetUsage_memmory()) {
        oprot.writeI64(struct.usage_memmory);
      }
      if (struct.isSetTotal_threads()) {
        oprot.writeI16(struct.total_threads);
      }
      if (struct.isSetRun_threads()) {
        oprot.writeI16(struct.run_threads);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, JavaStatistics struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(5);
      if (incoming.get(0)) {
        struct.java_version = iprot.readString();
        struct.setJava_versionIsSet(true);
      }
      if (incoming.get(1)) {
        struct.total_memmory = iprot.readI64();
        struct.setTotal_memmoryIsSet(true);
      }
      if (incoming.get(2)) {
        struct.usage_memmory = iprot.readI64();
        struct.setUsage_memmoryIsSet(true);
      }
      if (incoming.get(3)) {
        struct.total_threads = iprot.readI16();
        struct.setTotal_threadsIsSet(true);
      }
      if (incoming.get(4)) {
        struct.run_threads = iprot.readI16();
        struct.setRun_threadsIsSet(true);
      }
    }
  }

}

