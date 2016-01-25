package org.sqlite;

import com.sun.jna.Pointer;
import org.junit.Ignore;
import org.junit.Test;
import org.sqlite.SQLite.SQLite3Context;

import static org.junit.Assert.*;
import static org.sqlite.SQLite.*;

public class ConnTest {
	@Test
	public void checkLibversion() throws SQLiteException {
		assertTrue(Conn.libversion().startsWith("3"));
	}

	@Test
	public void checkOpenTempFile() throws SQLiteException {
		final Conn c = Conn.open(Conn.TEMP_FILE, OpenFlags.SQLITE_OPEN_READWRITE, null);
		assertNotNull(c);
		assertEquals(Conn.TEMP_FILE, c.getFilename());
		checkResult(c.close());
	}

	@Test
	public void checkOpenInMemoryDb() throws SQLiteException {
		final Conn c = open();
		assertNotNull(c);
		assertEquals("", c.getFilename());
		checkResult(c.close());
	}

	@Test
	public void checkInitialState() throws SQLiteException {
		final Conn c = open();
		assertEquals(0, c.getChanges());
		assertEquals(0, c.getTotalChanges());
		assertEquals(0, c.getLastInsertRowid());

		assertEquals(0, c.getErrCode());
		assertEquals(0, c.getExtendedErrcode());
		assertEquals("not an error", c.getErrMsg());
		checkResult(c.close());
	}

	@Test
	public void readOnly() throws SQLiteException {
		final Conn c = open();
		assertFalse("not read only", c.isReadOnly(null));
		assertFalse("not read only", c.isReadOnly("main"));
		checkResult(c.close());
	}

	@Test
	public void queryOnly() throws SQLiteException {
		if (Conn.libversionNumber() < 3008000) {
			return;
		}
		final Conn c = open();
		assertFalse("not query only", c.isQueryOnly(null));
		c.setQueryOnly(null, true);
		assertTrue("query only", c.isQueryOnly(null));
		checkResult(c.close());
	}

	@Test
	public void checkPrepare() throws SQLiteException {
		final Conn c = open();
		final Stmt s = c.prepare("SELECT 1", false);
		assertNotNull(s);
		s.close();
		checkResult(c.close());
	}

	@Test
	public void checkExec() throws SQLiteException {
		final Conn c = open();
		c.exec("DROP TABLE IF EXISTS test;\n" +
				"CREATE TABLE test (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
				" d REAL, i INTEGER, s TEXT); -- bim");

		final boolean[] metadata = c.getTableColumnMetadata("main", "test", "id");
		assertTrue(metadata[0]);
		assertTrue(metadata[1]);
		assertTrue(metadata[2]);
		checkResult(c.close());
	}

	@Test
	public void fastExec() throws SQLiteException {
		final Conn c = open();
		c.fastExec("PRAGMA encoding=\"UTF-8\"");
		checkResult(c.close());
	}

	@Test
	public void checkGetTableColumnMetadata() {
		// TODO
	}

	@Test
	public void enableFKey() throws SQLiteException {
		final Conn c = open();
		assertFalse(c.areForeignKeysEnabled());
		assertTrue(c.enableForeignKeys(true));
		assertTrue(c.areForeignKeysEnabled());
		checkResult(c.close());
	}

	@Test
	public void enableTriggers() throws SQLiteException {
		final Conn c = open();
		assertTrue(c.areTriggersEnabled());
		assertFalse(c.enableForeignKeys(false));
		assertFalse(c.areForeignKeysEnabled());
		checkResult(c.close());
	}

	@Test
	public void enableLoadExtension() throws SQLiteException {
		final Conn c = open();
		c.enableLoadExtension(true);
		checkResult(c.close());
	}

	@Ignore
	@Test
	public void loadExtension() throws SQLiteException {
		final Conn c = open();
		c.enableLoadExtension(true);
		final String errMsg = c.loadExtension("/home/gwen/C/sqlite-csv-ext/csv", null);
		assertNull(errMsg);
		checkResult(c.close());
	}

	@Test
	public void limit() throws SQLiteException {
		final Conn c = open();
		final int max = c.getLimit(SQLite.SQLITE_LIMIT_VARIABLE_NUMBER);
		assertEquals(max, c.setLimit(SQLite.SQLITE_LIMIT_VARIABLE_NUMBER, max+1));
		assertEquals(max, c.getLimit(SQLite.SQLITE_LIMIT_VARIABLE_NUMBER)); // SQLITE_MAX_VARIABLE_NUMBER
		assertEquals(max, c.setLimit(SQLite.SQLITE_LIMIT_VARIABLE_NUMBER, max-1));
		assertEquals(max-1, c.getLimit(SQLite.SQLITE_LIMIT_VARIABLE_NUMBER));
	}

	@Test
	public void trace() throws SQLiteException {
		final Conn c = open();
		final String[] traces = new String[1];
		c.trace(new TraceCallback() {
			private int i;

			@Override
			public void trace(String sql) {
				traces[i++] = sql;
			}
		}, null);
		final String sql = "SELECT 1";
		c.fastExec(sql);
		assertArrayEquals("traces", new String[]{sql}, traces);
	}

	@Test
	public void profile() throws SQLiteException {
		final Conn c = open();
		final String[] profiles = new String[1];
		c.profile(new ProfileCallback() {
			private int i;

			@Override
			public void profile(String sql, long ns) {
				profiles[i++] = sql;
			}
		}, null);
		final String sql = "SELECT 1";
		c.fastExec(sql);
		assertArrayEquals("profiles", new String[]{sql}, profiles);
	}

	@Test
	public void createScalarFunction() throws SQLiteException {
		final Conn c = open();
		c.createScalarFunction("test", 0, FunctionFlags.SQLITE_UTF8 | FunctionFlags.SQLITE_DETERMINISTIC, new ScalarCallback() {
			@Override
			public void invoke(SQLite3Context pCtx, Pointer[] args) {
				assertNotNull(pCtx);
				assertEquals(0, args.length);
				//assertNull(args);
				SQLite.sqlite3_result_null(pCtx);
			}
		});
		c.fastExec("SELECT test()");
		c.createScalarFunction("test", 0, 0, null);
		c.close();
	}

	@Test
	public void createScalarFunctionWithArg() throws SQLiteException {
		final Conn c = open();
		c.createScalarFunction("test", 2, FunctionFlags.SQLITE_UTF8 | FunctionFlags.SQLITE_DETERMINISTIC, new ScalarCallback() {
			@Override
			public void invoke(SQLite3Context pCtx, Pointer[] args) {
				assertNotNull(pCtx);
				assertEquals(2, args.length);
				final SQLite.SQLite3Value firstArg = new SQLite.SQLite3Value(args[0]);
				assertEquals(ColTypes.SQLITE_INTEGER, sqlite3_value_numeric_type(firstArg));
				final int value = sqlite3_value_int(firstArg);
				assertEquals(123456, value);
				final SQLite.SQLite3Value secondArg = new SQLite.SQLite3Value(args[1]);
				assertEquals(2, sqlite3_value_int(secondArg));
				sqlite3_result_int(pCtx, value);
			}
		});
		final Stmt stmt = c.prepare("SELECT test(123456, 2)", false);
		assertTrue(stmt.step(0));
		assertEquals(123456, stmt.getColumnInt(0));
		stmt.closeAndCheck();
		c.closeAndCheck();
	}

	@Test(expected = ConnException.class)
	public void closedConn() throws SQLiteException {
		final Conn c = open();
		c.close();
		c.getAutoCommit();
	}

	@Test
	public void virtualTable() throws SQLiteException {
		final Conn c = open();
		c.fastExec("CREATE VIRTUAL TABLE names USING fts4(name, desc, tokenize=porter)");
		c.closeAndCheck();
	}

	private static class ConnState {
		private boolean triggersEnabled = true;
		private String encoding = UTF_8_ECONDING;
		private boolean foreignKeys = false;
		private String journalMode = "memory";
		private String lockingMode = "normal";
		private boolean queryOnly = false;
		private boolean recursiveTriggers = false;
		private String synchronous = "2";
	}
	private static abstract class ConnStateTest {
		private final String uri;
		private final ConnState state;

		private ConnStateTest(String uri) {
			this.uri = uri;
			state = new ConnState();
			expected(state);
		}
		protected abstract void expected(ConnState s);
	}

	private static final ConnStateTest[] CONN_STATE_TESTS = new ConnStateTest[]{
			new ConnStateTest("file:memdb?mode=memory") {
				@Override
				protected void expected(ConnState s) {
				}
			},
			new ConnStateTest("file:memdb?mode=memory&enable_triggers=off") {
				@Override
				protected void expected(ConnState s) {
					s.triggersEnabled = false;
				}
			},
			/*new ConnStateTest("file:memdb?mode=memory&encoding=UTF-16") {
				@Override
				protected void expected(ConnState s) {
					//s.encoding = "UTF-16";
				}
			},*/
			new ConnStateTest("file:memdb?mode=memory&foreign_keys=on") {
				@Override
				protected void expected(ConnState s) {
					s.foreignKeys = true;
				}
			},
			new ConnStateTest("file:?journal_mode=off") {
				@Override
				protected void expected(ConnState s) {
					s.journalMode = "off";
				}
			},
			/*new ConnStateTest("file:memdb?mode=memory&lockingMode=EXCLUSIVE") {
				@Override
				protected void expected(ConnState s) {
					s.lockingMode = "off";
				}
			},*/
			new ConnStateTest("file:memdb?mode=memory&query_only=on") {
				@Override
				protected void expected(ConnState s) {
					s.queryOnly = true;
				}
			},
			new ConnStateTest("file:memdb?mode=memory&recursive_triggers=on") {
				@Override
				protected void expected(ConnState s) {
					s.recursiveTriggers = true;
				}
			},
	};
	@Test
	public void openUriQueryParameters() throws SQLiteException {
		for (ConnStateTest t: CONN_STATE_TESTS) {
			final Conn c = Conn.open(t.uri, OpenFlags.SQLITE_OPEN_READWRITE | OpenFlags.SQLITE_OPEN_URI, null);
			check(t.state, c);
			c.closeAndCheck();
		}
	}

	private static void check(ConnState state, Conn c) throws SQLiteException {
		assertEquals("triggersEnabled", state.triggersEnabled, c.areTriggersEnabled());
		assertEquals("encoding", state.encoding, c.encoding(null));
		assertEquals("foreignKeys", state.foreignKeys, c.areForeignKeysEnabled());
		assertEquals("journalMode", state.journalMode, pragma(c, OpenQueryParameter.JOURNAL_MODE.name));
		assertEquals("lockingMode", state.lockingMode, pragma(c, OpenQueryParameter.LOCKING_MODE.name));
		assertEquals("queryOnly", state.queryOnly, c.isQueryOnly(null));
		assertEquals("recursiveTriggers", state.recursiveTriggers, c.pragma(null, OpenQueryParameter.RECURSIVE_TRIGGERS.name));
		assertEquals("synchronous", state.synchronous, pragma(c, OpenQueryParameter.SYNCHRONOUS.name));
	}

	static void checkResult(int res) {
		assertEquals(0, res);
	}

	static Conn open() throws SQLiteException {
		final Conn conn = Conn.open(Conn.MEMORY, OpenFlags.SQLITE_OPEN_READWRITE | OpenFlags.SQLITE_OPEN_FULLMUTEX, null);
		conn.setAuhtorizer(new Authorizer() {
			@Override
			public int invoke(Pointer pArg, int actionCode, String arg1, String arg2, String dbName, String triggerName) {
				//System.out.println("pArg = [" + pArg + "], actionCode = [" + actionCode + "], arg1 = [" + arg1 + "], arg2 = [" + arg2 + "], dbName = [" + dbName + "], triggerName = [" + triggerName + "]");
				return Authorizer.SQLITE_OK;
			}
		}, null);
		return conn;
	}

	static String pragma(Conn c, String name) throws SQLiteException {
		Stmt s = null;
		try {
			s = c.prepare("PRAGMA " + name, false);
			if (!s.step(0)) {
				throw new StmtException(s, "No result", ErrCodes.WRAPPER_SPECIFIC);
			}
			return s.getColumnText(0);
		} finally {
			if (s != null) {
				s.close();
			}
		}
	}
}
