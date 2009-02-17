// Copyright 2008 Google Inc. All Rights Reserved.
package org.datanucleus.store.appengine.query;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.appengine.api.users.User;

import org.datanucleus.jdo.JDOQuery;
import org.datanucleus.query.expression.Expression;
import org.datanucleus.store.appengine.JDOTestCase;
import org.datanucleus.store.appengine.Utils;
import org.datanucleus.test.BidirectionalChildListJDO;
import org.datanucleus.test.Flight;
import static org.datanucleus.test.Flight.newFlightEntity;
import org.datanucleus.test.HasAncestorJDO;
import org.datanucleus.test.HasKeyAncestorKeyStringPkJDO;
import org.datanucleus.test.HasKeyPkJDO;
import org.datanucleus.test.HasOneToManyListJDO;
import org.datanucleus.test.HasOneToOneJDO;
import org.datanucleus.test.HasMultiValuePropsJDO;
import org.datanucleus.test.Person;
import org.datanucleus.test.KitchenSink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.jdo.Query;
import javax.jdo.JDOException;
import javax.jdo.JDOUserException;


/**
 * @author Max Ross <maxr@google.com>
 */
public class JDOQLQueryTest extends JDOTestCase {

  private static final List<SortPredicate> NO_SORTS = Collections.emptyList();
  private static final List<FilterPredicate> NO_FILTERS = Collections.emptyList();

  private static final FilterPredicate ORIGIN_EQ_2 =
      new FilterPredicate("origin", FilterOperator.EQUAL, 2L);
  private static final FilterPredicate ORIGIN_EQ_2STR =
      new FilterPredicate("origin", FilterOperator.EQUAL, "2");
  private static final FilterPredicate DEST_EQ_4 =
      new FilterPredicate("dest", FilterOperator.EQUAL, 4L);
  private static final FilterPredicate ORIG_GT_2 =
      new FilterPredicate("origin", FilterOperator.GREATER_THAN, 2L);
  private static final FilterPredicate ORIG_GTE_2 =
      new FilterPredicate("origin", FilterOperator.GREATER_THAN_OR_EQUAL, 2L);
  private static final FilterPredicate DEST_LT_4 =
      new FilterPredicate("dest", FilterOperator.LESS_THAN, 4L);
  private static final FilterPredicate DEST_LTE_4 =
      new FilterPredicate("dest", FilterOperator.LESS_THAN_OR_EQUAL, 4L);
  private static final SortPredicate ORIG_ASC =
      new SortPredicate("origin", SortDirection.ASCENDING);
  private static final SortPredicate DESC_DESC =
      new SortPredicate("dest", SortDirection.DESCENDING);

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    beginTxn();
  }

  @Override
  protected void tearDown() throws Exception {
    commitTxn();
    super.tearDown();
  }

  public void testUnsupportedFilters() {
    assertQueryUnsupportedByOrm("select from " + Flight.class.getName()
        + " where origin == 2 group by dest", DatastoreQuery.GROUP_BY_OP);
    // can't actually test having because the parser doesn't recognize it unless there is a
    // group by, and the group by gets seen first
    assertQueryUnsupportedByOrm("select from " + Flight.class.getName()
        + " where origin == 2 group by dest having dest == 2", DatastoreQuery.GROUP_BY_OP);
    Set<Expression.Operator> unsupportedOps = Utils.newHashSet(DatastoreQuery.UNSUPPORTED_OPERATORS);
    assertQueryUnsupportedByOrm(Flight.class,
        "origin == 2 || dest == 3", Expression.OP_OR, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "!origin", Expression.OP_NOT, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "(origin + dest) == 4", Expression.OP_ADD, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "origin + dest == 4", Expression.OP_ADD, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "(origin - dest) == 4", Expression.OP_SUB, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "origin - dest == 4", Expression.OP_SUB, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "(origin / dest) == 4", Expression.OP_DIV, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "origin / dest == 4", Expression.OP_DIV, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "(origin * dest) == 4", Expression.OP_MUL, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "origin * dest == 4", Expression.OP_MUL, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "(origin % dest) == 4", Expression.OP_MOD, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "origin % dest == 4", Expression.OP_MOD, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "origin | dest == 4", Expression.OP_OR, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "~origin == 4", Expression.OP_COM, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "!origin == 4", Expression.OP_NOT, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "-origin == 4", Expression.OP_NEG, unsupportedOps);
    assertQueryUnsupportedByOrm(Flight.class, "origin instanceof " + Flight.class.getName(),
        Expression.OP_IS, unsupportedOps);
    assertEquals(Utils.<Expression.Operator>newHashSet(Expression.OP_CONCAT, Expression.OP_LIKE,
        Expression.OP_BETWEEN, Expression.OP_ISNOT), unsupportedOps);
    // multiple inequality filters
    // TODO(maxr) Make this pass against the real datastore.
    // We need to have it return BadRequest instead of NeedIndex for that to
    // happen.
    assertQueryUnsupportedByDatastore("select from " + Flight.class.getName()
        + " where (origin > 2 && dest < 4)");
    // inequality filter prop is not the same as the first order by prop
    assertQueryUnsupportedByDatastore("select from " + Flight.class.getName()
        + " where origin > 2 order by dest");
  }

  public void testSupportedFilters() {
    assertQuerySupported(Flight.class, "", NO_FILTERS, NO_SORTS);
    assertQuerySupported(Flight.class, "origin == 2", Utils.newArrayList(ORIGIN_EQ_2), NO_SORTS);
    assertQuerySupported(
        Flight.class, "origin == \"2\"", Utils.newArrayList(ORIGIN_EQ_2STR), NO_SORTS);
    assertQuerySupported(Flight.class, "(origin == 2)", Utils.newArrayList(ORIGIN_EQ_2), NO_SORTS);
    assertQuerySupported(Flight.class, "origin == 2 && dest == 4", Utils.newArrayList(ORIGIN_EQ_2,
        DEST_EQ_4), NO_SORTS);
    assertQuerySupported(Flight.class, "(origin == 2 && dest == 4)", Utils.newArrayList(ORIGIN_EQ_2,
        DEST_EQ_4), NO_SORTS);
    assertQuerySupported(Flight.class, "(origin == 2) && (dest == 4)", Utils.newArrayList(
        ORIGIN_EQ_2, DEST_EQ_4), NO_SORTS);

    assertQuerySupported(Flight.class, "origin > 2", Utils.newArrayList(ORIG_GT_2), NO_SORTS);
    assertQuerySupported(Flight.class, "origin >= 2", Utils.newArrayList(ORIG_GTE_2), NO_SORTS);
    assertQuerySupported(Flight.class, "dest < 4", Utils.newArrayList(DEST_LT_4), NO_SORTS);
    assertQuerySupported(Flight.class, "dest <= 4", Utils.newArrayList(DEST_LTE_4), NO_SORTS);

    assertQuerySupported("select from " + Flight.class.getName() + " order by origin asc",
        NO_FILTERS, Utils.newArrayList(ORIG_ASC));
    assertQuerySupported("select from " + Flight.class.getName() + " order by dest desc",
        NO_FILTERS, Utils.newArrayList(DESC_DESC));
    assertQuerySupported("select from " + Flight.class.getName()
        + " order by origin asc, dest desc", NO_FILTERS, Utils.newArrayList(ORIG_ASC, DESC_DESC));

    assertQuerySupported("select from " + Flight.class.getName()
        + " where origin == 2 && dest == 4 order by origin asc, dest desc",
        Utils.newArrayList(ORIGIN_EQ_2, DEST_EQ_4), Utils.newArrayList(ORIG_ASC, DESC_DESC));
  }

  public void testBindVariables() {
    String queryStr = "select from " + Flight.class.getName() + " where origin == two ";
    assertQuerySupported(queryStr + " parameters String two",
        Utils.newArrayList(ORIGIN_EQ_2STR), NO_SORTS, "2");
    assertQuerySupportedWithExplicitParams(queryStr,
        Utils.newArrayList(ORIGIN_EQ_2STR), NO_SORTS, "String two", "2");

    queryStr = "select from " + Flight.class.getName() + " where origin == two && dest == four ";
    assertQuerySupported(queryStr + "parameters int two, int four",
        Utils.newArrayList(ORIGIN_EQ_2, DEST_EQ_4), NO_SORTS, 2L, 4L);
    assertQuerySupportedWithExplicitParams(queryStr,
        Utils.newArrayList(ORIGIN_EQ_2, DEST_EQ_4), NO_SORTS, "int two, int four", 2L, 4L);

    queryStr = "select from " + Flight.class.getName() + " where origin == two && dest == four ";
    String orderStr = "order by origin asc, dest desc";
    assertQuerySupported(queryStr + "parameters int two, int four " + orderStr,
        Utils.newArrayList(ORIGIN_EQ_2, DEST_EQ_4),
        Utils.newArrayList(ORIG_ASC, DESC_DESC), 2L, 4L);
    assertQuerySupportedWithExplicitParams(queryStr + orderStr,
        Utils.newArrayList(ORIGIN_EQ_2, DEST_EQ_4),
        Utils.newArrayList(ORIG_ASC, DESC_DESC), "int two, int four", 2L, 4L);
  }

  public void test2Equals2OrderBy() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 2));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("3", "yam", "bam", 2, 1));
    ldth.ds.put(newFlightEntity("4", "yam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "notyam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "yam", "notbam", 2, 2));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where origin == \"yam\" && dest == \"bam\""
            + " order by you asc, me desc");
    @SuppressWarnings("unchecked")
    List<Flight> result = (List<Flight>) q.execute();
    assertEquals(4, result.size());

    assertEquals("1", result.get(0).getName());
    assertEquals("2", result.get(1).getName());
    assertEquals("4", result.get(2).getName());
    assertEquals("3", result.get(3).getName());
  }

  public void testSetFilter() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 2, 2));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName());
    q.setFilter("origin == \"yam\" && you == 2");
    @SuppressWarnings("unchecked")
    List<Flight> result = (List<Flight>) q.execute();
    assertEquals(1, result.size());
  }

  // TODO(maxr) The filter is invalid because it uses
  // 'AND' instead of '&&'.  When we fix bug 1596691
  // this test will start to fail (good!), and at that
  // point we can modify this test to veirify the correct
  // behavior instead of the incorrect behavior.
  public void testSetInvalidFilter() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 2, 2));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName());
    q.setFilter("origin == \"yam\" AND you == 2");
    @SuppressWarnings("unchecked")
    List<Flight> result = (List<Flight>) q.execute();
    assertEquals(2, result.size());
  }

  public void testDefaultOrderingIsAsc() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 2));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("3", "yam", "bam", 2, 1));
    ldth.ds.put(newFlightEntity("4", "yam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "notyam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "yam", "notbam", 2, 2));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where origin == \"yam\" && dest == \"bam\""
            + " order by you");
    @SuppressWarnings("unchecked")
    List<Flight> result = (List<Flight>) q.execute();
    assertEquals(4, result.size());

    assertEquals("1", result.get(0).getName());
    assertEquals("2", result.get(1).getName());
    assertEquals("3", result.get(2).getName());
    assertEquals("4", result.get(3).getName());
  }

  public void testLimitQuery() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 2));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("3", "yam", "bam", 2, 1));
    ldth.ds.put(newFlightEntity("4", "yam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "notyam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "yam", "notbam", 2, 2));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where origin == \"yam\" && dest == \"bam\""
            + " order by you asc, me desc");

    q.setRange(Long.MAX_VALUE, 1);
    @SuppressWarnings("unchecked")
    List<Flight> result1 = (List<Flight>) q.execute();
    assertEquals(1, result1.size());
    assertEquals("1", result1.get(0).getName());

    q.setRange(Long.MAX_VALUE, Long.MAX_VALUE);
    @SuppressWarnings("unchecked")
    List<Flight> result2 = (List<Flight>) q.execute();
    assertEquals(4, result2.size());
    assertEquals("1", result2.get(0).getName());

    q.setRange(Long.MAX_VALUE, 0);
    @SuppressWarnings("unchecked")
    List<Flight> result3 = (List<Flight>) q.execute();
    assertEquals(0, result3.size());

    q.setRange(Long.MAX_VALUE, -1);
    try {
      q.execute();
      fail("expected iae");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  public void testOffsetQuery() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 2));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("3", "yam", "bam", 2, 1));
    ldth.ds.put(newFlightEntity("4", "yam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "notyam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "yam", "notbam", 2, 2));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where origin == \"yam\" && dest == \"bam\""
            + " order by you asc, me desc");

    q.setRange(0, Long.MAX_VALUE);
    @SuppressWarnings("unchecked")
    List<Flight> result1 = (List<Flight>) q.execute();
    assertEquals(4, result1.size());
    assertEquals("1", result1.get(0).getName());

    q.setRange(1, Long.MAX_VALUE);
    @SuppressWarnings("unchecked")
    List<Flight> result2 = (List<Flight>) q.execute();
    assertEquals(3, result2.size());
    assertEquals("2", result2.get(0).getName());

    q.setRange(Long.MAX_VALUE, Long.MAX_VALUE);
    @SuppressWarnings("unchecked")
    List<Flight> result3 = (List<Flight>) q.execute();
    assertEquals(4, result3.size());
    assertEquals("1", result3.get(0).getName());

    q.setRange(-1, Long.MAX_VALUE);
    try {
      q.execute();
      fail("expected iae");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  public void testOffsetLimitQuery() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 2));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("3", "yam", "bam", 2, 1));
    ldth.ds.put(newFlightEntity("4", "yam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "notyam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "yam", "notbam", 2, 2));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where origin == \"yam\" && dest == \"bam\""
            + " order by you asc, me desc");

    q.setRange(0, 0);
    @SuppressWarnings("unchecked")
    List<Flight> result1 = (List<Flight>) q.execute();
    assertEquals(0, result1.size());

    q.setRange(1, 0);
    @SuppressWarnings("unchecked")
    List<Flight> result2 = (List<Flight>) q.execute();
    assertEquals(0, result2.size());

    q.setRange(0, 1);
    @SuppressWarnings("unchecked")
    List<Flight> result3 = (List<Flight>) q.execute();
    assertEquals(1, result3.size());

    q.setRange(0, 2);
    @SuppressWarnings("unchecked")
    List<Flight> result4 = (List<Flight>) q.execute();
    assertEquals(2, result4.size());
    assertEquals("1", result4.get(0).getName());

    q.setRange(1, 2);
    @SuppressWarnings("unchecked")
    List<Flight> result5 = (List<Flight>) q.execute();
    assertEquals(1, result5.size());
    assertEquals("2", result5.get(0).getName());

    q.setRange(2, 5);
    @SuppressWarnings("unchecked")
    List<Flight> result6 = (List<Flight>) q.execute();
    assertEquals(2, result6.size());
    assertEquals("4", result6.get(0).getName());

    q.setRange(2, 2);
    @SuppressWarnings("unchecked")
    List<Flight> result7 = (List<Flight>) q.execute();
    assertEquals(0, result7.size());

    q.setRange(2, 1);
    @SuppressWarnings("unchecked")
    List<Flight> result8 = (List<Flight>) q.execute();
    assertEquals(0, result8.size());
  }

  public void testOffsetLimitSingleStringQuery() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 2));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 1, 1));
    ldth.ds.put(newFlightEntity("3", "yam", "bam", 2, 1));
    ldth.ds.put(newFlightEntity("4", "yam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "notyam", "bam", 2, 2));
    ldth.ds.put(newFlightEntity("5", "yam", "notbam", 2, 2));
    String queryFormat =
        "select from " + Flight.class.getName()
            + " where origin == \"yam\" && dest == \"bam\""
            + " order by you asc, me desc range %d,%d";
    Query q = pm.newQuery(String.format(queryFormat, 0, 0));
    @SuppressWarnings("unchecked")
    List<Flight> result1 = (List<Flight>) q.execute();
    assertEquals(0, result1.size());

    q = pm.newQuery(String.format(queryFormat, 1, 0));
    @SuppressWarnings("unchecked")
    List<Flight> result2 = (List<Flight>) q.execute();
    assertEquals(0, result2.size());

    q = pm.newQuery(String.format(queryFormat, 0, 1));
    @SuppressWarnings("unchecked")
    List<Flight> result3 = (List<Flight>) q.execute();
    assertEquals(1, result3.size());

    q = pm.newQuery(String.format(queryFormat, 0, 2));
    @SuppressWarnings("unchecked")
    List<Flight> result4 = (List<Flight>) q.execute();
    assertEquals(2, result4.size());
    assertEquals("1", result4.get(0).getName());

    q = pm.newQuery(String.format(queryFormat, 1, 2));
    @SuppressWarnings("unchecked")
    List<Flight> result5 = (List<Flight>) q.execute();
    assertEquals(1, result5.size());
    assertEquals("2", result5.get(0).getName());

    q = pm.newQuery(String.format(queryFormat, 2, 5));
    @SuppressWarnings("unchecked")
    List<Flight> result6 = (List<Flight>) q.execute();
    assertEquals(2, result6.size());
    assertEquals("4", result6.get(0).getName());

    q = pm.newQuery(String.format(queryFormat, 2, 2));
    @SuppressWarnings("unchecked")
    List<Flight> result7 = (List<Flight>) q.execute();
    assertEquals(0, result7.size());

    q = pm.newQuery(String.format(queryFormat, 2, 1));
    @SuppressWarnings("unchecked")
    List<Flight> result8 = (List<Flight>) q.execute();
    assertEquals(0, result8.size());
  }

  public void testSerialization() throws IOException {
    Query q = pm.newQuery("select from " + Flight.class.getName());
    q.execute();

    JDOQLQuery innerQuery = (JDOQLQuery)((JDOQuery)q).getInternalQuery();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    // the fact that this doesn't blow up is the test
    oos.writeObject(innerQuery);
  }

  public void testKeyQuery_StringPk() {
    Entity flightEntity = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity);

    Query q = pm.newQuery(
        "select from " + Flight.class.getName() + " where id == key parameters String key");
    @SuppressWarnings("unchecked")
    List<Flight> flights = (List<Flight>) q.execute(KeyFactory.keyToString(flightEntity.getKey()));
    assertEquals(1, flights.size());
    assertEquals(flightEntity.getKey(), KeyFactory.stringToKey(flights.get(0).getId()));
  }

  public void testKeyQuery_KeyPk() {
    Entity e = new Entity(HasKeyPkJDO.class.getSimpleName());
    ldth.ds.put(e);

    Query q = pm.newQuery(
        "select from " + HasKeyPkJDO.class.getName() + " where key == mykey parameters String mykey");
    @SuppressWarnings("unchecked")
    List<HasKeyPkJDO> result = (List<HasKeyPkJDO>) q.execute(e.getKey());
    assertEquals(1, result.size());
    assertEquals(e.getKey(), result.get(0).getKey());
  }

  public void testKeyQueryWithSorts() {
    Entity flightEntity = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity);

    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where id == key parameters String key order by id asc");
    @SuppressWarnings("unchecked")
    List<Flight> flights = (List<Flight>) q.execute(KeyFactory.keyToString(flightEntity.getKey()));
    assertEquals(1, flights.size());
    assertEquals(flightEntity.getKey(), KeyFactory.stringToKey(flights.get(0).getId()));
  }

  public void testKeyQuery_MultipleFilters() {
    Entity flightEntity = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity);

    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where id == key && origin == \"yam\" parameters String key");
    @SuppressWarnings("unchecked")
    List<Flight> flights = (List<Flight>) q.execute(KeyFactory.keyToString(flightEntity.getKey()));
    assertEquals(1, flights.size());
    assertEquals(flightEntity.getKey(), KeyFactory.stringToKey(flights.get(0).getId()));
  }

  public void testKeyQuery_NonEqualityFilter() {
    Entity flightEntity1 = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity1);
    Entity flightEntity2 = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity2);

    Query q = pm.newQuery(
        "select from " + Flight.class.getName() + " where id > key parameters String key");
    @SuppressWarnings("unchecked")
    List<Flight> flights = (List<Flight>) q.execute(KeyFactory.keyToString(flightEntity1.getKey()));
    assertEquals(1, flights.size());
    assertEquals(flightEntity2.getKey(), KeyFactory.stringToKey(flights.get(0).getId()));
  }

  public void testKeyQuery_SortByKey() {
    Entity flightEntity1 = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity1);

    Entity flightEntity2 = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity2);

    Query q = pm.newQuery(
        "select from " + Flight.class.getName() + " where origin == 'yam' order by id DESC");
    @SuppressWarnings("unchecked")
    List<Flight> flights = (List<Flight>) q.execute();
    assertEquals(2, flights.size());
    assertEquals(flightEntity2.getKey(), KeyFactory.stringToKey(flights.get(0).getId()));
    assertEquals(flightEntity1.getKey(), KeyFactory.stringToKey(flights.get(1).getId()));
  }

  public void testAncestorQueryWithStringAncestor() {
    Entity flightEntity = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity);
    Entity hasAncestorEntity = new Entity(HasAncestorJDO.class.getSimpleName(), flightEntity.getKey());
    ldth.ds.put(hasAncestorEntity);

    Query q = pm.newQuery(
        "select from " + HasAncestorJDO.class.getName()
            + " where ancestorId == ancId parameters String ancId");
    @SuppressWarnings("unchecked")
    List<HasAncestorJDO> haList =
        (List<HasAncestorJDO>) q.execute(KeyFactory.keyToString(flightEntity.getKey()));
    assertEquals(1, haList.size());
    assertEquals(flightEntity.getKey(), KeyFactory.stringToKey(haList.get(0).getAncestorId()));

    assertEquals(
        flightEntity.getKey(), getDatastoreQuery(q).getMostRecentDatastoreQuery().getAncestor());
    assertEquals(NO_FILTERS, getFilterPredicates(q));
    assertEquals(NO_SORTS, getSortPredicates(q));
  }

  public void testAncestorQueryWithKeyAncestor() {
    Entity e = new Entity("parent");
    ldth.ds.put(e);
    Entity childEntity = new Entity(HasKeyAncestorKeyStringPkJDO.class.getSimpleName(), e.getKey());
    ldth.ds.put(childEntity);

    Query q = pm.newQuery(
        "select from " + HasKeyAncestorKeyStringPkJDO.class.getName()
            + " where ancestorKey == ancId parameters " + Key.class.getName() + " ancId");
    @SuppressWarnings("unchecked")
    List<HasKeyAncestorKeyStringPkJDO> result =
        (List<HasKeyAncestorKeyStringPkJDO>) q.execute(e.getKey());
    assertEquals(1, result.size());
    assertEquals(e.getKey(), result.get(0).getAncestorKey());
  }

  public void testIllegalAncestorQuery_BadOperator() {
    Entity flightEntity = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity);
    Entity hasAncestorEntity = new Entity(HasAncestorJDO.class.getName(), flightEntity.getKey());
    ldth.ds.put(hasAncestorEntity);

    Query q = pm.newQuery(
        "select from " + HasAncestorJDO.class.getName()
            + " where ancestorId > ancId parameters String ancId");
    try {
      q.execute(KeyFactory.keyToString(flightEntity.getKey()));
      fail ("expected udfe");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException udfe) {
      // good
    }
  }

  public void testSortByFieldWithCustomColumn() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", 1, 2, 400));
    ldth.ds.put(newFlightEntity("2", "yam", "bam", 1, 1, 300));
    ldth.ds.put(newFlightEntity("3", "yam", "bam", 2, 1, 200));
    Query q = pm.newQuery(
        "select from " + Flight.class.getName()
            + " where origin == \"yam\" && dest == \"bam\""
            + " order by flightNumber asc");
    @SuppressWarnings("unchecked")
    List<Flight> result = (List<Flight>) q.execute();
    assertEquals(3, result.size());

    assertEquals("3", result.get(0).getName());
    assertEquals("2", result.get(1).getName());
    assertEquals("1", result.get(2).getName());
  }

  public void testIllegalAncestorQuery_SortByAncestor() {
    Entity flightEntity = newFlightEntity("1", "yam", "bam", 1, 2);
    ldth.ds.put(flightEntity);
    Entity hasAncestorEntity = new Entity(HasAncestorJDO.class.getName(), flightEntity.getKey());
    ldth.ds.put(hasAncestorEntity);

    Query q = pm.newQuery(
        "select from " + HasAncestorJDO.class.getName()
            + " where ancestorId == ancId parameters String ancId order by ancestorId ASC");
    try {
      q.execute(KeyFactory.keyToString(flightEntity.getKey()));
      fail ("expected udfe");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException udfe) {
      // good
    }
  }

  public void testFilterByChildObject() {
    Entity parentEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity flightEntity = newFlightEntity(parentEntity.getKey(), null, "f", "bos", "mia", 2, 4, 33);
    ldth.ds.put(flightEntity);

    Flight flight =
        pm.getObjectById(Flight.class, KeyFactory.keyToString(flightEntity.getKey()));
    Query q = pm.newQuery(
        "select from " + HasOneToOneJDO.class.getName()
        + " where flight == f parameters " + Flight.class.getName() + " f");
    List<HasOneToOneJDO> result = (List<HasOneToOneJDO>) q.execute(flight);
    assertEquals(1, result.size());
    assertEquals(parentEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
  }

  public void testFilterByChildObject_AdditionalFilterOnParent() {
    Entity parentEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity flightEntity = newFlightEntity(parentEntity.getKey(), null, "f", "bos", "mia", 2, 4, 33);
    ldth.ds.put(flightEntity);

    Flight flight =
        pm.getObjectById(Flight.class, KeyFactory.keyToString(flightEntity.getKey()));
    Query q = pm.newQuery(
        "select from " + HasOneToOneJDO.class.getName()
        + " where id == parentId && flight == f "
        + "parameters String parentId, " + Flight.class.getName() + " f");
    List<HasOneToOneJDO> result = (List<HasOneToOneJDO>) q.execute(KeyFactory.keyToString(flightEntity.getKey()), flight);
    assertTrue(result.isEmpty());

    result = (List<HasOneToOneJDO>) q.execute(KeyFactory.keyToString(parentEntity.getKey()), flight);
    assertEquals(1, result.size());
    assertEquals(parentEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
  }

  public void testFilterByChildObject_UnsupportedOperator() {
    Entity parentEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity flightEntity = newFlightEntity(parentEntity.getKey(), null, "f", "bos", "mia", 2, 4, 33);
    ldth.ds.put(flightEntity);

    Flight flight =
        pm.getObjectById(Flight.class, KeyFactory.keyToString(flightEntity.getKey()));
    Query q = pm.newQuery(
        "select from " + HasOneToOneJDO.class.getName()
        + " where flight > f parameters " + Flight.class.getName() + " f");
    try {
      q.execute(flight);
      fail("expected udfe");
    } catch (DatastoreQuery.UnsupportedDatastoreFeatureException udfe) {
      // good
    }
  }

  public void testFilterByChildObject_ValueWithoutAncestor() {
    Entity parentEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity flightEntity = newFlightEntity("f", "bos", "mia", 2, 4, 33);
    ldth.ds.put(flightEntity);

    Flight flight =
        pm.getObjectById(Flight.class, KeyFactory.keyToString(flightEntity.getKey()));
    Query q = pm.newQuery(
        "select from " + HasOneToOneJDO.class.getName()
        + " where flight == f parameters " + Flight.class.getName() + " f");
    try {
      q.execute(flight);
      fail("expected JDOException");
    } catch (JDOException e) {
      // good
    }
  }

  public void testFilterByChildObject_KeyIsWrongType() {
    Entity parentEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    ldth.ds.put(parentEntity);

    Query q = pm.newQuery(
        "select from " + HasOneToOneJDO.class.getName()
        + " where flight == f parameters " + Flight.class.getName() + " f");
    try {
      q.execute(parentEntity.getKey());
      fail("expected JDOException");
    } catch (JDOException e) {
      // good
    }
  }

  public void testFilterByChildObject_KeyParentIsWrongType() {
    Key parent = KeyFactory.createKey("yar", 44);
    Entity flightEntity = new Entity(Flight.class.getSimpleName(), parent);

    Query q = pm.newQuery(
        "select from " + HasOneToOneJDO.class.getName()
        + " where flight == f parameters " + Flight.class.getName() + " f");
    try {
      q.execute(flightEntity.getKey());
      fail("expected JDOException");
    } catch (JDOException e) {
      // good
    }
  }

  public void testFilterByChildObject_ValueWithoutId() {
    Entity parentEntity = new Entity(HasOneToOneJDO.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity flightEntity = newFlightEntity("f", "bos", "mia", 2, 4, 33);
    ldth.ds.put(flightEntity);

    Flight flight = new Flight();
    Query q = pm.newQuery(
        "select from " + HasOneToOneJDO.class.getName()
        + " where flight == f parameters " + Flight.class.getName() + " f");
    try {
      q.execute(flight);
      fail("expected JDOException");
    } catch (JDOException e) {
      // good
    }
  }

  public void testFilterByParentObject() {
    Entity parentEntity = new Entity(HasOneToManyListJDO.class.getSimpleName());
    ldth.ds.put(parentEntity);
    Entity bidirEntity = new Entity(BidirectionalChildListJDO.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity);
    Entity bidirEntity2 = new Entity(BidirectionalChildListJDO.class.getSimpleName(), parentEntity.getKey());
    ldth.ds.put(bidirEntity2);

    HasOneToManyListJDO parent =
        pm.getObjectById(HasOneToManyListJDO.class, KeyFactory.keyToString(parentEntity.getKey()));
    Query q = pm.newQuery(
        "select from " + BidirectionalChildListJDO.class.getName()
        + " where parent == p parameters " + HasOneToManyListJDO.class.getName() + " p");
    List<BidirectionalChildListJDO> result = (List<BidirectionalChildListJDO>) q.execute(parent);
    assertEquals(2, result.size());
    assertEquals(bidirEntity.getKey(), KeyFactory.stringToKey(result.get(0).getId()));
    assertEquals(bidirEntity2.getKey(), KeyFactory.stringToKey(result.get(1).getId()));
  }

  public void testFilterByMultiValueProperty() {
    Entity entity = new Entity(HasMultiValuePropsJDO.class.getSimpleName());
    entity.setProperty("strList", Utils.newArrayList("1", "2", "3"));
    entity.setProperty("keyList",
        Utils.newArrayList(KeyFactory.createKey("be", "bo"), KeyFactory.createKey("bo", "be")));
    ldth.ds.put(entity);

    Query q = pm.newQuery(
        "select from " + HasMultiValuePropsJDO.class.getName()
        + " where strList == p1 && strList == p2 parameters String p1, String p2");
    List<HasMultiValuePropsJDO> result = (List<HasMultiValuePropsJDO>) q.execute("1", "3");
    assertEquals(1, result.size());
    result = (List<HasMultiValuePropsJDO>) q.execute("1", "4");
    assertEquals(0, result.size());

    q = pm.newQuery(
        "select from " + HasMultiValuePropsJDO.class.getName()
        + " where keyList == p1 && keyList == p2 parameters " + Key.class.getName() + " p1, "
        + Key.class.getName() + " p2");
    result = (List<HasMultiValuePropsJDO>) q.execute(KeyFactory.createKey("be", "bo"), KeyFactory.createKey("bo", "be"));
    assertEquals(1, result.size());
    result = (List<HasMultiValuePropsJDO>) q.execute(KeyFactory.createKey("be", "bo"), KeyFactory.createKey("bo", "be2"));
    assertEquals(0, result.size());
  }

  public void testFilterByEmbeddedField() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = pm.newQuery(
        "select from " + Person.class.getName()
        + " where name.first == \"max\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.execute();
    assertEquals(1, result.size());
  }

  public void testFilterByEmbeddedField_OverriddenColumn() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = pm.newQuery(
        "select from " + Person.class.getName()
        + " where anotherName.last == \"notross\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.execute();
    assertEquals(1, result.size());
  }

  public void testFilterByEmbeddedField_MultipleFields() {
    Entity entity = new Entity(Person.class.getSimpleName());
    entity.setProperty("first", "max");
    entity.setProperty("last", "ross");
    entity.setProperty("anotherFirst", "notmax");
    entity.setProperty("anotherLast", "notross");
    ldth.ds.put(entity);

    Query q = pm.newQuery(
        "select from " + Person.class.getName()
        + " where name.first == \"max\" && anotherName.last == \"notross\"");
    @SuppressWarnings("unchecked")
    List<Person> result = (List<Person>) q.execute();
    assertEquals(1, result.size());
  }

  public void testFilterBySubObject_UnknownField() {
    try {
      pm.newQuery(
          "select from " + Flight.class.getName() + " where origin.first == \"max\"").execute();
      fail("expected exception");
    } catch (JDOUserException e) {
      // good
    }
  }

  public void testFilterBySubObject_NotEmbeddable() {
    try {
      pm.newQuery(
          "select from " + HasOneToOneJDO.class.getName() + " where flight.origin == \"max\"").execute();
      fail("expected exception");
    } catch (JDOUserException e) {
      // good
    }
  }

  public void testUserQuery() {
    Entity e = KitchenSink.newKitchenSinkEntity("blarg", null);
    ldth.ds.put(e);

    Query q = pm.newQuery(
        "select from " + KitchenSink.class.getName() + " where userVal == u parameters " + User.class.getName() + " u");
    @SuppressWarnings("unchecked")
    List<KitchenSink> results = (List<KitchenSink>) q.execute(KitchenSink.USER1);
    assertEquals(1, results.size());

    Query q2 = pm.newQuery(KitchenSink.class, "userVal == u");
    q2.declareParameters(User.class.getName() + " u");
    @SuppressWarnings("unchecked")
    List<KitchenSink> results2 = (List<KitchenSink>) q2.execute(KitchenSink.USER1);
    assertEquals(1, results2.size());
  }

  public void testQueryWithNegativeLiteralLong() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", -1, 2));

    Query q = pm.newQuery(
        "select from " + Flight.class.getName() + " where you == -1");
    @SuppressWarnings("unchecked")
    List<Flight> results = (List<Flight>) q.execute();
    assertEquals(1, results.size());
    q = pm.newQuery(
        "select from " + Flight.class.getName() + " where you > -2");
    @SuppressWarnings("unchecked")
    List<Flight> results2 = (List<Flight>) q.execute();
    assertEquals(1, results2.size());
  }

  public void testQueryWithNegativeLiteralDouble() {
    ldth.ds.put(KitchenSink.newKitchenSinkEntity("blarg", null));

    Query q = pm.newQuery(
        "select from " + KitchenSink.class.getName() + " where doubleVal > -2.25");
    @SuppressWarnings("unchecked")
    List<KitchenSink> results = (List<KitchenSink>) q.execute();
    assertEquals(1, results.size());
  }

  public void testQueryWithNegativeParam() {
    ldth.ds.put(newFlightEntity("1", "yam", "bam", -1, 2));

    Query q = pm.newQuery(
        "select from " + Flight.class.getName() + " where you == p parameters String p");
    @SuppressWarnings("unchecked")
    List<Flight> results = (List<Flight>) q.execute(-1);
    assertEquals(1, results.size());
  }

  private void assertQueryUnsupportedByOrm(
      Class<?> clazz, String query, Expression.Operator unsupportedOp,
      Set<Expression.Operator> unsupportedOps) {
    Query q = pm.newQuery(clazz, query);
    try {
      q.execute();
      fail("expected UnsupportedOperationException for query <" + query + ">");
    } catch (DatastoreQuery.UnsupportedDatastoreOperatorException uoe) {
      // good
      assertEquals(unsupportedOp, uoe.getOperation());
    }
    unsupportedOps.remove(unsupportedOp);
  }

  private void assertQueryUnsupportedByDatastore(String query) {
    Query q = pm.newQuery(query);
    try {
      q.execute();
      fail("expected IllegalArgumentException for query <" + query + ">");
    } catch (IllegalArgumentException iae) {
      // good
    }
  }

  private void assertQueryUnsupportedByOrm(String query, Expression.Operator unsupportedOp) {
    Query q = pm.newQuery(query);
    try {
      q.execute();
      fail("expected UnsupportedOperationException for query <" + query + ">");
    } catch (DatastoreQuery.UnsupportedDatastoreOperatorException uoe) {
      // good
      assertEquals(unsupportedOp, uoe.getOperation());
    }
  }

  private void assertQuerySupported(Class<?> clazz, String query,
      List<FilterPredicate> addedFilters, List<SortPredicate> addedSorts, Object... bindVariables) {
    Query q;
    if (query.equals("")) {
      q = pm.newQuery(clazz);
    } else {
      q = pm.newQuery(clazz, query);
    }
    assertQuerySupported(q, addedFilters, addedSorts, bindVariables);
  }

  private void assertQuerySupported(String query,
      List<FilterPredicate> addedFilters, List<SortPredicate> addedSorts, Object... bindVariables) {
    assertQuerySupported(pm.newQuery(query), addedFilters, addedSorts, bindVariables);
  }

  private void assertQuerySupported(Query q, List<FilterPredicate> addedFilters,
      List<SortPredicate> addedSorts, Object... bindVariables) {
    if (bindVariables.length == 0) {
      q.execute();
    } else if (bindVariables.length == 1) {
      q.execute(bindVariables[0]);
    } else if (bindVariables.length == 2) {
      q.execute(bindVariables[0], bindVariables[1]);
    }
    assertEquals(addedFilters, getFilterPredicates(q));
    assertEquals(addedSorts, getSortPredicates(q));
  }

  private DatastoreQuery getDatastoreQuery(Query q) {
    return ((JDOQLQuery)((JDOQuery)q).getInternalQuery()).getDatastoreQuery();
}

  private List<FilterPredicate> getFilterPredicates(Query q) {
    return getDatastoreQuery(q).getMostRecentDatastoreQuery().getFilterPredicates();
  }

  private List<SortPredicate> getSortPredicates(Query q) {
    return getDatastoreQuery(q).getMostRecentDatastoreQuery().getSortPredicates();
  }

  private void assertQuerySupportedWithExplicitParams(String query,
      List<FilterPredicate> addedFilters, List<SortPredicate> addedSorts, String explicitParams,
      Object... bindVariables) {
    Query q = pm.newQuery(query);
    q.declareParameters(explicitParams);
    if (bindVariables.length == 0) {
      q.execute();
    } else if (bindVariables.length == 1) {
      q.execute(bindVariables[0]);
    } else if (bindVariables.length == 2) {
      q.execute(bindVariables[0], bindVariables[1]);
    }
    assertEquals(addedFilters, getFilterPredicates(q));
    assertEquals(addedSorts, getSortPredicates(q));
  }
}