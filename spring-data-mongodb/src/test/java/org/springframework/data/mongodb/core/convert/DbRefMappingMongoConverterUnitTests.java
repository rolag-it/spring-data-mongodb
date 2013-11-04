/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core.convert;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.data.mongodb.core.convert.LazyLoadingTestUtils.*;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;
import org.springframework.data.mongodb.core.convert.MappingMongoConverterUnitTests.Person;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.util.SerializationUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBRef;

/**
 * @author Oliver Gierke
 */
@RunWith(MockitoJUnitRunner.class)
public class DbRefMappingMongoConverterUnitTests {

	MappingMongoConverter converter;
	MongoMappingContext mappingContext;

	@Mock MongoDbFactory dbFactory;

	@Before
	public void setUp() {

		when(dbFactory.getExceptionTranslator()).thenReturn(new MongoExceptionTranslator());

		this.mappingContext = new MongoMappingContext();
		this.converter = new MappingMongoConverter(new DefaultDbRefResolver(dbFactory), mappingContext);
	}

	/**
	 * @see DATAMONGO-347
	 */
	@Test
	public void createsSimpleDBRefCorrectly() {

		Person person = new Person();
		person.id = "foo";

		DBRef dbRef = converter.toDBRef(person, null);
		assertThat(dbRef.getId(), is((Object) "foo"));
		assertThat(dbRef.getRef(), is("person"));
	}

	/**
	 * @see DATAMONGO-657
	 */
	@Test
	public void convertDocumentWithMapDBRef() {

		MapDBRef mapDBRef = new MapDBRef();

		MapDBRefVal val = new MapDBRefVal();
		val.id = BigInteger.ONE;

		Map<String, MapDBRefVal> mapVal = new HashMap<String, MapDBRefVal>();
		mapVal.put("test", val);

		mapDBRef.map = mapVal;

		BasicDBObject dbObject = new BasicDBObject();
		converter.write(mapDBRef, dbObject);

		DBObject map = (DBObject) dbObject.get("map");

		assertThat(map.get("test"), instanceOf(DBRef.class));

		DBObject mapValDBObject = new BasicDBObject();
		mapValDBObject.put("_id", BigInteger.ONE);

		DBRef dbRef = mock(DBRef.class);
		when(dbRef.fetch()).thenReturn(mapValDBObject);

		((DBObject) dbObject.get("map")).put("test", dbRef);

		MapDBRef read = converter.read(MapDBRef.class, dbObject);

		assertThat(read.map.get("test").id, is(BigInteger.ONE));
	}

	/**
	 * @see DATAMONGO-347
	 */
	@Test
	public void createsDBRefWithClientSpecCorrectly() {

		PropertyPath path = PropertyPath.from("person", PersonClient.class);
		MongoPersistentProperty property = mappingContext.getPersistentPropertyPath(path).getLeafProperty();

		Person person = new Person();
		person.id = "foo";

		DBRef dbRef = converter.toDBRef(person, property);
		assertThat(dbRef.getId(), is((Object) "foo"));
		assertThat(dbRef.getRef(), is("person"));
	}

	/**
	 * @see DATAMONGO-348
	 */
	@Test
	public void lazyLoadingProxyForLazyDbRefOnInterface() {

		String id = "42";
		String value = "bubu";
		MappingMongoConverter converterSpy = spy(converter);
		doReturn(new BasicDBObject("_id", id).append("value", value)).when(converterSpy).readRef((DBRef) any());

		BasicDBObject dbo = new BasicDBObject();
		ClassWithLazyDbRefs lazyDbRefs = new ClassWithLazyDbRefs();
		lazyDbRefs.dbRefToInterface = new LinkedList<LazyDbRefTarget>(Arrays.asList(new LazyDbRefTarget("1")));
		converterSpy.write(lazyDbRefs, dbo);

		ClassWithLazyDbRefs result = converterSpy.read(ClassWithLazyDbRefs.class, dbo);

		assertProxyIsResolved(result.dbRefToInterface, false);
		assertThat(result.dbRefToInterface.get(0).getId(), is(id));
		assertProxyIsResolved(result.dbRefToInterface, true);
		assertThat(result.dbRefToInterface.get(0).getValue(), is(value));
	}

	/**
	 * @see DATAMONGO-348
	 */
	@Test
	public void lazyLoadingProxyForLazyDbRefOnConcreteCollection() {

		String id = "42";
		String value = "bubu";
		MappingMongoConverter converterSpy = spy(converter);
		doReturn(new BasicDBObject("_id", id).append("value", value)).when(converterSpy).readRef((DBRef) any());

		BasicDBObject dbo = new BasicDBObject();
		ClassWithLazyDbRefs lazyDbRefs = new ClassWithLazyDbRefs();
		lazyDbRefs.dbRefToConcreteCollection = new ArrayList<LazyDbRefTarget>(Arrays.asList(new LazyDbRefTarget(id, value)));
		converterSpy.write(lazyDbRefs, dbo);

		ClassWithLazyDbRefs result = converterSpy.read(ClassWithLazyDbRefs.class, dbo);

		assertProxyIsResolved(result.dbRefToConcreteCollection, false);
		assertThat(result.dbRefToConcreteCollection.get(0).getId(), is(id));
		assertProxyIsResolved(result.dbRefToConcreteCollection, true);
		assertThat(result.dbRefToConcreteCollection.get(0).getValue(), is(value));
	}

	/**
	 * @see DATAMONGO-348
	 */
	@Test
	public void lazyLoadingProxyForLazyDbRefOnConcreteType() {

		String id = "42";
		String value = "bubu";
		MappingMongoConverter converterSpy = spy(converter);
		doReturn(new BasicDBObject("_id", id).append("value", value)).when(converterSpy).readRef((DBRef) any());

		BasicDBObject dbo = new BasicDBObject();
		ClassWithLazyDbRefs lazyDbRefs = new ClassWithLazyDbRefs();
		lazyDbRefs.dbRefToConcreteType = new LazyDbRefTarget(id, value);
		converterSpy.write(lazyDbRefs, dbo);

		ClassWithLazyDbRefs result = converterSpy.read(ClassWithLazyDbRefs.class, dbo);

		assertProxyIsResolved(result.dbRefToConcreteType, false);
		assertThat(result.dbRefToConcreteType.getId(), is(id));
		assertProxyIsResolved(result.dbRefToConcreteType, true);
		assertThat(result.dbRefToConcreteType.getValue(), is(value));
	}

	/**
	 * @see DATAMONGO-348
	 */
	@Test
	public void lazyLoadingProxyForLazyDbRefOnConcreteTypeWithPersistenceConstructor() {

		String id = "42";
		String value = "bubu";
		MappingMongoConverter converterSpy = spy(converter);
		doReturn(new BasicDBObject("_id", id).append("value", value)).when(converterSpy).readRef((DBRef) any());

		BasicDBObject dbo = new BasicDBObject();
		ClassWithLazyDbRefs lazyDbRefs = new ClassWithLazyDbRefs();
		lazyDbRefs.dbRefToConcreteTypeWithPersistenceConstructor = new LazyDbRefTargetWithPeristenceConstructor(
				(Object) id, (Object) value);
		converterSpy.write(lazyDbRefs, dbo);

		ClassWithLazyDbRefs result = converterSpy.read(ClassWithLazyDbRefs.class, dbo);

		assertProxyIsResolved(result.dbRefToConcreteTypeWithPersistenceConstructor, false);
		assertThat(result.dbRefToConcreteTypeWithPersistenceConstructor.getId(), is(id));
		assertProxyIsResolved(result.dbRefToConcreteTypeWithPersistenceConstructor, true);
		assertThat(result.dbRefToConcreteTypeWithPersistenceConstructor.getValue(), is(value));
	}

	/**
	 * @see DATAMONGO-348
	 */
	@Test
	public void lazyLoadingProxyForLazyDbRefOnConcreteTypeWithPersistenceConstructorButWithoutDefaultConstructor() {

		String id = "42";
		String value = "bubu";
		MappingMongoConverter converterSpy = spy(converter);
		doReturn(new BasicDBObject("_id", id).append("value", value)).when(converterSpy).readRef((DBRef) any());

		BasicDBObject dbo = new BasicDBObject();
		ClassWithLazyDbRefs lazyDbRefs = new ClassWithLazyDbRefs();
		lazyDbRefs.dbRefToConcreteTypeWithPersistenceConstructorWithoutDefaultConstructor = new LazyDbRefTargetWithPeristenceConstructorWithoutDefaultConstructor(
				(Object) id, (Object) value);
		converterSpy.write(lazyDbRefs, dbo);

		ClassWithLazyDbRefs result = converterSpy.read(ClassWithLazyDbRefs.class, dbo);

		assertProxyIsResolved(result.dbRefToConcreteTypeWithPersistenceConstructorWithoutDefaultConstructor, false);
		assertThat(result.dbRefToConcreteTypeWithPersistenceConstructorWithoutDefaultConstructor.getId(), is(id));
		assertProxyIsResolved(result.dbRefToConcreteTypeWithPersistenceConstructorWithoutDefaultConstructor, true);
		assertThat(result.dbRefToConcreteTypeWithPersistenceConstructorWithoutDefaultConstructor.getValue(), is(value));
	}

	/**
	 * @see DATAMONGO-348
	 */
	@Test
	public void lazyLoadingProxyForSerializableLazyDbRefOnConcreteType() {

		String id = "42";
		String value = "bubu";
		MappingMongoConverter converterSpy = spy(converter);
		doReturn(new BasicDBObject("_id", id).append("value", value)).when(converterSpy).readRef((DBRef) any());

		BasicDBObject dbo = new BasicDBObject();
		SerializableClassWithLazyDbRefs lazyDbRefs = new SerializableClassWithLazyDbRefs();
		lazyDbRefs.dbRefToSerializableTarget = new SerializableLazyDbRefTarget(id, value);
		converterSpy.write(lazyDbRefs, dbo);

		SerializableClassWithLazyDbRefs result = converterSpy.read(SerializableClassWithLazyDbRefs.class, dbo);

		SerializableClassWithLazyDbRefs deserializedResult = (SerializableClassWithLazyDbRefs) transport(result);

		assertThat(deserializedResult.dbRefToSerializableTarget.getId(), is(id));
		assertProxyIsResolved(deserializedResult.dbRefToSerializableTarget, true);
		assertThat(deserializedResult.dbRefToSerializableTarget.getValue(), is(value));
	}

	private Object transport(Object result) {
		return SerializationUtils.deserialize(SerializationUtils.serialize(result));
	}

	class MapDBRef {
		@org.springframework.data.mongodb.core.mapping.DBRef Map<String, MapDBRefVal> map;
	}

	class MapDBRefVal {
		BigInteger id;
	}

	class PersonClient {
		@org.springframework.data.mongodb.core.mapping.DBRef Person person;
	}

	static class ClassWithLazyDbRefs {

		@Id String id;
		@org.springframework.data.mongodb.core.mapping.DBRef(lazy = true) List<LazyDbRefTarget> dbRefToInterface;
		@org.springframework.data.mongodb.core.mapping.DBRef(lazy = true) ArrayList<LazyDbRefTarget> dbRefToConcreteCollection;
		@org.springframework.data.mongodb.core.mapping.DBRef(lazy = true) LazyDbRefTarget dbRefToConcreteType;
		@org.springframework.data.mongodb.core.mapping.DBRef(lazy = true) LazyDbRefTargetWithPeristenceConstructor dbRefToConcreteTypeWithPersistenceConstructor;
		@org.springframework.data.mongodb.core.mapping.DBRef(lazy = true) LazyDbRefTargetWithPeristenceConstructorWithoutDefaultConstructor dbRefToConcreteTypeWithPersistenceConstructorWithoutDefaultConstructor;
	}

	static class SerializableClassWithLazyDbRefs implements Serializable {

		private static final long serialVersionUID = 1L;

		@org.springframework.data.mongodb.core.mapping.DBRef(lazy = true) SerializableLazyDbRefTarget dbRefToSerializableTarget;
	}

	static class LazyDbRefTarget implements Serializable {

		private static final long serialVersionUID = 1L;

		@Id String id;
		String value;

		public LazyDbRefTarget() {
			this(null);
		}

		public LazyDbRefTarget(String id) {
			this(id, null);
		}

		public LazyDbRefTarget(String id, String value) {
			this.id = id;
			this.value = value;
		}

		public String getId() {
			return id;
		}

		public String getValue() {
			return value;
		}
	}

	static class LazyDbRefTargetWithPeristenceConstructor extends LazyDbRefTarget {

		boolean persistenceConstructorCalled;

		public LazyDbRefTargetWithPeristenceConstructor() {}

		@PersistenceConstructor
		public LazyDbRefTargetWithPeristenceConstructor(String id, String value) {
			super(id, value);
			this.persistenceConstructorCalled = true;
		}

		public LazyDbRefTargetWithPeristenceConstructor(Object id, Object value) {
			super(id.toString(), value.toString());
		}
	}

	static class LazyDbRefTargetWithPeristenceConstructorWithoutDefaultConstructor extends LazyDbRefTarget {

		boolean persistenceConstructorCalled;

		@PersistenceConstructor
		public LazyDbRefTargetWithPeristenceConstructorWithoutDefaultConstructor(String id, String value) {
			super(id, value);
			this.persistenceConstructorCalled = true;
		}

		public LazyDbRefTargetWithPeristenceConstructorWithoutDefaultConstructor(Object id, Object value) {
			super(id.toString(), value.toString());
		}
	}

	static class SerializableLazyDbRefTarget extends LazyDbRefTarget implements Serializable {

		public SerializableLazyDbRefTarget() {}

		public SerializableLazyDbRefTarget(String id, String value) {
			super(id, value);
		}

		private static final long serialVersionUID = 1L;
	}
}
