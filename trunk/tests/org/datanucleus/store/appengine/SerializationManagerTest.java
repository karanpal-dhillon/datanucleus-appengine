// Copyright 2009 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine;

import com.google.appengine.api.datastore.Blob;

import junit.framework.TestCase;

import org.datanucleus.JDOClassLoaderResolver;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.ExtensionMetaData;
import org.datanucleus.test.HasSerializableJDO;

import java.io.Serializable;

/**
 * SerializationManager tests.
 *
 * @author Max Ross <maxr@google.com>
 */
public class SerializationManagerTest extends TestCase {

  public void testDeserialize_NonSerializableClass() {
    try {
      SerializationManager.DEFAULT_SERIALIZATION_STRATEGY.deserialize(new Blob("".getBytes()), getClass());
      fail("Expcted NucleusException");
    } catch (NucleusException ne) {
      // good
    }
  }

  public static final class MySerializable1 implements Serializable {}
  public static final class MySerializable2 implements Serializable {}

  public void testDeserialize_BytesAreOfWrongType() {
    MySerializable1 ser1 = new MySerializable1();
    Blob ser1Blob = SerializationManager.DEFAULT_SERIALIZATION_STRATEGY.serialize(ser1);
    try {
      SerializationManager.DEFAULT_SERIALIZATION_STRATEGY.deserialize(ser1Blob, MySerializable2.class);
      fail("Expcted NucleusException");
    } catch (NucleusException ne) {
      // good
    }
  }

  public void testGetDefaultSerializer() {
    SerializationManager mgr = new SerializationManager();
    AbstractMemberMetaData ammd = new AbstractMemberMetaData(null, "yar") {};
    assertSame(SerializationManager.DEFAULT_SERIALIZATION_STRATEGY, mgr.getSerializationStrategy(new JDOClassLoaderResolver(), ammd));
  }

  public void testGetCustomSerializer() {
    SerializationManager mgr = new SerializationManager();
    AbstractMemberMetaData ammd = new AbstractMemberMetaData(null, "yar") {
      @Override
      public ExtensionMetaData[] getExtensions() {
        ExtensionMetaData emd = new ExtensionMetaData(
            "datanucleus",
            SerializationManager.SERIALIZATION_STRATEGY_KEY,
            HasSerializableJDO.ProtocolBufferSerializationStrategy.class.getName());
        return new ExtensionMetaData[] {emd};
      }
    };
    SerializationStrategy serializationStrategy = mgr.getSerializationStrategy(new JDOClassLoaderResolver(), ammd);
    assertTrue(serializationStrategy instanceof HasSerializableJDO.ProtocolBufferSerializationStrategy);
  }

  public void testGetCustomSerializer_BadClass() {
    SerializationManager mgr = new SerializationManager();
    AbstractMemberMetaData ammd = new AbstractMemberMetaData(null, "yar") {
      @Override
      public ExtensionMetaData[] getExtensions() {
        ExtensionMetaData emd = new ExtensionMetaData(
            "datanucleus",
            SerializationManager.SERIALIZATION_STRATEGY_KEY,
            getClass().getName());
        return new ExtensionMetaData[] {emd};
      }
    };
    try {
      mgr.getSerializationStrategy(new JDOClassLoaderResolver(), ammd);
      fail("Expectd NucleusException");
    } catch (NucleusException ne) {
      // good
    }
  }
}