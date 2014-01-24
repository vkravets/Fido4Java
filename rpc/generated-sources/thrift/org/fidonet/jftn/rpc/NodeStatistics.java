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

public class NodeStatistics implements org.apache.thrift.TBase<NodeStatistics, NodeStatistics._Fields>, java.io.Serializable, Cloneable, Comparable<NodeStatistics> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("NodeStatistics");

  private static final org.apache.thrift.protocol.TField NODE_VERSION_FIELD_DESC = new org.apache.thrift.protocol.TField("node_version", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField CONNECTED_USER_FIELD_DESC = new org.apache.thrift.protocol.TField("connected_user", org.apache.thrift.protocol.TType.I16, (short)2);
  private static final org.apache.thrift.protocol.TField TOTAL_AREAS_FIELD_DESC = new org.apache.thrift.protocol.TField("total_areas", org.apache.thrift.protocol.TType.I64, (short)3);
  private static final org.apache.thrift.protocol.TField TOTAL_MESSAGES_FIELD_DESC = new org.apache.thrift.protocol.TField("total_messages", org.apache.thrift.protocol.TType.I64, (short)4);
  private static final org.apache.thrift.protocol.TField MESSAGES_BY_DAY_FIELD_DESC = new org.apache.thrift.protocol.TField("messages_by_day", org.apache.thrift.protocol.TType.I64, (short)5);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new NodeStatisticsStandardSchemeFactory());
    schemes.put(TupleScheme.class, new NodeStatisticsTupleSchemeFactory());
  }

  public String node_version; // required
  public short connected_user; // required
  public long total_areas; // required
  public long total_messages; // required
  public long messages_by_day; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    NODE_VERSION((short)1, "node_version"),
    CONNECTED_USER((short)2, "connected_user"),
    TOTAL_AREAS((short)3, "total_areas"),
    TOTAL_MESSAGES((short)4, "total_messages"),
    MESSAGES_BY_DAY((short)5, "messages_by_day");

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
        case 1: // NODE_VERSION
          return NODE_VERSION;
        case 2: // CONNECTED_USER
          return CONNECTED_USER;
        case 3: // TOTAL_AREAS
          return TOTAL_AREAS;
        case 4: // TOTAL_MESSAGES
          return TOTAL_MESSAGES;
        case 5: // MESSAGES_BY_DAY
          return MESSAGES_BY_DAY;
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
  private static final int __CONNECTED_USER_ISSET_ID = 0;
  private static final int __TOTAL_AREAS_ISSET_ID = 1;
  private static final int __TOTAL_MESSAGES_ISSET_ID = 2;
  private static final int __MESSAGES_BY_DAY_ISSET_ID = 3;
  private byte __isset_bitfield = 0;
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.NODE_VERSION, new org.apache.thrift.meta_data.FieldMetaData("node_version", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.CONNECTED_USER, new org.apache.thrift.meta_data.FieldMetaData("connected_user", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I16)));
    tmpMap.put(_Fields.TOTAL_AREAS, new org.apache.thrift.meta_data.FieldMetaData("total_areas", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.TOTAL_MESSAGES, new org.apache.thrift.meta_data.FieldMetaData("total_messages", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    tmpMap.put(_Fields.MESSAGES_BY_DAY, new org.apache.thrift.meta_data.FieldMetaData("messages_by_day", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(NodeStatistics.class, metaDataMap);
  }

  public NodeStatistics() {
  }

  public NodeStatistics(
    String node_version,
    short connected_user,
    long total_areas,
    long total_messages,
    long messages_by_day)
  {
    this();
    this.node_version = node_version;
    this.connected_user = connected_user;
    setConnected_userIsSet(true);
    this.total_areas = total_areas;
    setTotal_areasIsSet(true);
    this.total_messages = total_messages;
    setTotal_messagesIsSet(true);
    this.messages_by_day = messages_by_day;
    setMessages_by_dayIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public NodeStatistics(NodeStatistics other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetNode_version()) {
      this.node_version = other.node_version;
    }
    this.connected_user = other.connected_user;
    this.total_areas = other.total_areas;
    this.total_messages = other.total_messages;
    this.messages_by_day = other.messages_by_day;
  }

  public NodeStatistics deepCopy() {
    return new NodeStatistics(this);
  }

  @Override
  public void clear() {
    this.node_version = null;
    setConnected_userIsSet(false);
    this.connected_user = 0;
    setTotal_areasIsSet(false);
    this.total_areas = 0;
    setTotal_messagesIsSet(false);
    this.total_messages = 0;
    setMessages_by_dayIsSet(false);
    this.messages_by_day = 0;
  }

  public String getNode_version() {
    return this.node_version;
  }

  public NodeStatistics setNode_version(String node_version) {
    this.node_version = node_version;
    return this;
  }

  public void unsetNode_version() {
    this.node_version = null;
  }

  /** Returns true if field node_version is set (has been assigned a value) and false otherwise */
  public boolean isSetNode_version() {
    return this.node_version != null;
  }

  public void setNode_versionIsSet(boolean value) {
    if (!value) {
      this.node_version = null;
    }
  }

  public short getConnected_user() {
    return this.connected_user;
  }

  public NodeStatistics setConnected_user(short connected_user) {
    this.connected_user = connected_user;
    setConnected_userIsSet(true);
    return this;
  }

  public void unsetConnected_user() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __CONNECTED_USER_ISSET_ID);
  }

  /** Returns true if field connected_user is set (has been assigned a value) and false otherwise */
  public boolean isSetConnected_user() {
    return EncodingUtils.testBit(__isset_bitfield, __CONNECTED_USER_ISSET_ID);
  }

  public void setConnected_userIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __CONNECTED_USER_ISSET_ID, value);
  }

  public long getTotal_areas() {
    return this.total_areas;
  }

  public NodeStatistics setTotal_areas(long total_areas) {
    this.total_areas = total_areas;
    setTotal_areasIsSet(true);
    return this;
  }

  public void unsetTotal_areas() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __TOTAL_AREAS_ISSET_ID);
  }

  /** Returns true if field total_areas is set (has been assigned a value) and false otherwise */
  public boolean isSetTotal_areas() {
    return EncodingUtils.testBit(__isset_bitfield, __TOTAL_AREAS_ISSET_ID);
  }

  public void setTotal_areasIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __TOTAL_AREAS_ISSET_ID, value);
  }

  public long getTotal_messages() {
    return this.total_messages;
  }

  public NodeStatistics setTotal_messages(long total_messages) {
    this.total_messages = total_messages;
    setTotal_messagesIsSet(true);
    return this;
  }

  public void unsetTotal_messages() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __TOTAL_MESSAGES_ISSET_ID);
  }

  /** Returns true if field total_messages is set (has been assigned a value) and false otherwise */
  public boolean isSetTotal_messages() {
    return EncodingUtils.testBit(__isset_bitfield, __TOTAL_MESSAGES_ISSET_ID);
  }

  public void setTotal_messagesIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __TOTAL_MESSAGES_ISSET_ID, value);
  }

  public long getMessages_by_day() {
    return this.messages_by_day;
  }

  public NodeStatistics setMessages_by_day(long messages_by_day) {
    this.messages_by_day = messages_by_day;
    setMessages_by_dayIsSet(true);
    return this;
  }

  public void unsetMessages_by_day() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __MESSAGES_BY_DAY_ISSET_ID);
  }

  /** Returns true if field messages_by_day is set (has been assigned a value) and false otherwise */
  public boolean isSetMessages_by_day() {
    return EncodingUtils.testBit(__isset_bitfield, __MESSAGES_BY_DAY_ISSET_ID);
  }

  public void setMessages_by_dayIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __MESSAGES_BY_DAY_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case NODE_VERSION:
      if (value == null) {
        unsetNode_version();
      } else {
        setNode_version((String)value);
      }
      break;

    case CONNECTED_USER:
      if (value == null) {
        unsetConnected_user();
      } else {
        setConnected_user((Short)value);
      }
      break;

    case TOTAL_AREAS:
      if (value == null) {
        unsetTotal_areas();
      } else {
        setTotal_areas((Long)value);
      }
      break;

    case TOTAL_MESSAGES:
      if (value == null) {
        unsetTotal_messages();
      } else {
        setTotal_messages((Long)value);
      }
      break;

    case MESSAGES_BY_DAY:
      if (value == null) {
        unsetMessages_by_day();
      } else {
        setMessages_by_day((Long)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case NODE_VERSION:
      return getNode_version();

    case CONNECTED_USER:
      return Short.valueOf(getConnected_user());

    case TOTAL_AREAS:
      return Long.valueOf(getTotal_areas());

    case TOTAL_MESSAGES:
      return Long.valueOf(getTotal_messages());

    case MESSAGES_BY_DAY:
      return Long.valueOf(getMessages_by_day());

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case NODE_VERSION:
      return isSetNode_version();
    case CONNECTED_USER:
      return isSetConnected_user();
    case TOTAL_AREAS:
      return isSetTotal_areas();
    case TOTAL_MESSAGES:
      return isSetTotal_messages();
    case MESSAGES_BY_DAY:
      return isSetMessages_by_day();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof NodeStatistics)
      return this.equals((NodeStatistics)that);
    return false;
  }

  public boolean equals(NodeStatistics that) {
    if (that == null)
      return false;

    boolean this_present_node_version = true && this.isSetNode_version();
    boolean that_present_node_version = true && that.isSetNode_version();
    if (this_present_node_version || that_present_node_version) {
      if (!(this_present_node_version && that_present_node_version))
        return false;
      if (!this.node_version.equals(that.node_version))
        return false;
    }

    boolean this_present_connected_user = true;
    boolean that_present_connected_user = true;
    if (this_present_connected_user || that_present_connected_user) {
      if (!(this_present_connected_user && that_present_connected_user))
        return false;
      if (this.connected_user != that.connected_user)
        return false;
    }

    boolean this_present_total_areas = true;
    boolean that_present_total_areas = true;
    if (this_present_total_areas || that_present_total_areas) {
      if (!(this_present_total_areas && that_present_total_areas))
        return false;
      if (this.total_areas != that.total_areas)
        return false;
    }

    boolean this_present_total_messages = true;
    boolean that_present_total_messages = true;
    if (this_present_total_messages || that_present_total_messages) {
      if (!(this_present_total_messages && that_present_total_messages))
        return false;
      if (this.total_messages != that.total_messages)
        return false;
    }

    boolean this_present_messages_by_day = true;
    boolean that_present_messages_by_day = true;
    if (this_present_messages_by_day || that_present_messages_by_day) {
      if (!(this_present_messages_by_day && that_present_messages_by_day))
        return false;
      if (this.messages_by_day != that.messages_by_day)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder();

    boolean present_node_version = true && (isSetNode_version());
    builder.append(present_node_version);
    if (present_node_version)
      builder.append(node_version);

    boolean present_connected_user = true;
    builder.append(present_connected_user);
    if (present_connected_user)
      builder.append(connected_user);

    boolean present_total_areas = true;
    builder.append(present_total_areas);
    if (present_total_areas)
      builder.append(total_areas);

    boolean present_total_messages = true;
    builder.append(present_total_messages);
    if (present_total_messages)
      builder.append(total_messages);

    boolean present_messages_by_day = true;
    builder.append(present_messages_by_day);
    if (present_messages_by_day)
      builder.append(messages_by_day);

    return builder.toHashCode();
  }

  @Override
  public int compareTo(NodeStatistics other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetNode_version()).compareTo(other.isSetNode_version());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetNode_version()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.node_version, other.node_version);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetConnected_user()).compareTo(other.isSetConnected_user());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetConnected_user()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.connected_user, other.connected_user);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetTotal_areas()).compareTo(other.isSetTotal_areas());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTotal_areas()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.total_areas, other.total_areas);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetTotal_messages()).compareTo(other.isSetTotal_messages());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTotal_messages()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.total_messages, other.total_messages);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetMessages_by_day()).compareTo(other.isSetMessages_by_day());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMessages_by_day()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.messages_by_day, other.messages_by_day);
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
    StringBuilder sb = new StringBuilder("NodeStatistics(");
    boolean first = true;

    sb.append("node_version:");
    if (this.node_version == null) {
      sb.append("null");
    } else {
      sb.append(this.node_version);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("connected_user:");
    sb.append(this.connected_user);
    first = false;
    if (!first) sb.append(", ");
    sb.append("total_areas:");
    sb.append(this.total_areas);
    first = false;
    if (!first) sb.append(", ");
    sb.append("total_messages:");
    sb.append(this.total_messages);
    first = false;
    if (!first) sb.append(", ");
    sb.append("messages_by_day:");
    sb.append(this.messages_by_day);
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

  private static class NodeStatisticsStandardSchemeFactory implements SchemeFactory {
    public NodeStatisticsStandardScheme getScheme() {
      return new NodeStatisticsStandardScheme();
    }
  }

  private static class NodeStatisticsStandardScheme extends StandardScheme<NodeStatistics> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, NodeStatistics struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // NODE_VERSION
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.node_version = iprot.readString();
              struct.setNode_versionIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // CONNECTED_USER
            if (schemeField.type == org.apache.thrift.protocol.TType.I16) {
              struct.connected_user = iprot.readI16();
              struct.setConnected_userIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // TOTAL_AREAS
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.total_areas = iprot.readI64();
              struct.setTotal_areasIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // TOTAL_MESSAGES
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.total_messages = iprot.readI64();
              struct.setTotal_messagesIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // MESSAGES_BY_DAY
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.messages_by_day = iprot.readI64();
              struct.setMessages_by_dayIsSet(true);
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, NodeStatistics struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.node_version != null) {
        oprot.writeFieldBegin(NODE_VERSION_FIELD_DESC);
        oprot.writeString(struct.node_version);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(CONNECTED_USER_FIELD_DESC);
      oprot.writeI16(struct.connected_user);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(TOTAL_AREAS_FIELD_DESC);
      oprot.writeI64(struct.total_areas);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(TOTAL_MESSAGES_FIELD_DESC);
      oprot.writeI64(struct.total_messages);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(MESSAGES_BY_DAY_FIELD_DESC);
      oprot.writeI64(struct.messages_by_day);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class NodeStatisticsTupleSchemeFactory implements SchemeFactory {
    public NodeStatisticsTupleScheme getScheme() {
      return new NodeStatisticsTupleScheme();
    }
  }

  private static class NodeStatisticsTupleScheme extends TupleScheme<NodeStatistics> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, NodeStatistics struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetNode_version()) {
        optionals.set(0);
      }
      if (struct.isSetConnected_user()) {
        optionals.set(1);
      }
      if (struct.isSetTotal_areas()) {
        optionals.set(2);
      }
      if (struct.isSetTotal_messages()) {
        optionals.set(3);
      }
      if (struct.isSetMessages_by_day()) {
        optionals.set(4);
      }
      oprot.writeBitSet(optionals, 5);
      if (struct.isSetNode_version()) {
        oprot.writeString(struct.node_version);
      }
      if (struct.isSetConnected_user()) {
        oprot.writeI16(struct.connected_user);
      }
      if (struct.isSetTotal_areas()) {
        oprot.writeI64(struct.total_areas);
      }
      if (struct.isSetTotal_messages()) {
        oprot.writeI64(struct.total_messages);
      }
      if (struct.isSetMessages_by_day()) {
        oprot.writeI64(struct.messages_by_day);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, NodeStatistics struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(5);
      if (incoming.get(0)) {
        struct.node_version = iprot.readString();
        struct.setNode_versionIsSet(true);
      }
      if (incoming.get(1)) {
        struct.connected_user = iprot.readI16();
        struct.setConnected_userIsSet(true);
      }
      if (incoming.get(2)) {
        struct.total_areas = iprot.readI64();
        struct.setTotal_areasIsSet(true);
      }
      if (incoming.get(3)) {
        struct.total_messages = iprot.readI64();
        struct.setTotal_messagesIsSet(true);
      }
      if (incoming.get(4)) {
        struct.messages_by_day = iprot.readI64();
        struct.setMessages_by_dayIsSet(true);
      }
    }
  }

}

