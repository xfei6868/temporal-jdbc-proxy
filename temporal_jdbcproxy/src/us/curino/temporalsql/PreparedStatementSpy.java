/**
 *  Copyright 2011 The Temporal JDBC Proxy AUTHORS AND CONTRIBUTORS
 *  
 *  See the NOTICE file distributed with this work for additional 
 *  information regarding copyright ownership.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package us.curino.temporalsql;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;



import com.relationalcloud.tsqlparser.Parser;
import com.relationalcloud.tsqlparser.parser.ParseException;

/**
 * Wraps a PreparedStatement and reports method calls, returns and exceptions.
 *
 * @author Arthur Blake
 */
public class PreparedStatementSpy extends StatementSpy implements PreparedStatement
{


	public int numberOfNonTemporalParameters = -1;	

	/**
	 * holds list of bind variables for tracing
	 */
	protected final List argTrace = new ArrayList();

	// a way to turn on and off type help...
	// todo:  make this a configurable parameter
	// todo, debug arrays and streams in a more useful manner.... if possible
	private static final boolean showTypeHelp = false;

	/**
	 * Store an argument (bind variable) into the argTrace list (above) for later dumping.
	 *
	 * @param i          index of argument being set.
	 * @param typeHelper optional additional info about the type that is being set in the arg
	 * @param arg        argument being bound.
	 */
	protected void argTraceSet(int i, String typeHelper, Object arg)
	{
		String tracedArg;
		try
		{
			tracedArg = rdbmsSpecifics.formatParameterObject(arg);
		}
		catch (Throwable t)
		{
			// rdbmsSpecifics should NEVER EVER throw an exception!!
			// but just in case it does, we trap it.
			log.debug("rdbmsSpecifics threw an exception while trying to format a " +
					"parameter object [" + arg + "] this is very bad!!! (" +
					t.getMessage() + ")");

			// backup - so that at least we won't harm the application using us
			tracedArg = arg==null?"null":arg.toString();
		}

		i--;  // make the index 0 based
		synchronized (argTrace)
		{
			// if an object is being inserted out of sequence, fill up missing values with null...
			while (i >= argTrace.size())
			{
				argTrace.add(argTrace.size(), null);
			}
			if (!showTypeHelp || typeHelper == null)
			{
				argTrace.set(i, tracedArg);
			}
			else
			{
				argTrace.set(i, typeHelper + tracedArg);
			}
		}
	}

	private String sql;
	private List<String> sqls;

	protected String dumpedSql()
	{
		StringBuffer dumpSql = new StringBuffer();
		int lastPos = 0;
		int Qpos = sql.indexOf('?', lastPos);  // find position of first question mark
		int argIdx = 0;
		String arg;

		while (Qpos != -1)
		{
			// get stored argument
			synchronized (argTrace)
			{
				try
				{
					arg = (String) argTrace.get(argIdx);
				}
				catch (IndexOutOfBoundsException e)
				{
					arg = "?";
				}
			}
			if (arg == null)
			{
				arg = "?";
			}

			argIdx++;

			dumpSql.append(sql.substring(lastPos, Qpos));  // dump segment of sql up to question mark.
			lastPos = Qpos + 1;
			Qpos = sql.indexOf('?', lastPos);
			dumpSql.append(arg);
		}
		if (lastPos < sql.length())
		{
			dumpSql.append(sql.substring(lastPos, sql.length()));  // dump last segment
		}

		return dumpSql.toString();
	}

	protected void reportAllReturns(String methodCall, String msg)
	{
		log.methodReturned(this, methodCall, msg);
	}

	/**
	 * The real PreparedStatement that this PreparedStatementSpy wraps.
	 */
	//protected PreparedStatement realPreparedStatement;

	/**
	 * Get the real PreparedStatement that this PreparedStatementSpy wraps.
	 *
	 * @return the real PreparedStatement that this PreparedStatementSpy wraps.
	 */
	public ArrayList<PreparedStatement> getRealPreparedStatements()
	{
		return realSqls;
	}

	/**
	 * RdbmsSpecifics for formatting SQL for the given RDBMS.
	 */
	protected RdbmsSpecifics rdbmsSpecifics;

	public  ArrayList<PreparedStatement> realSqls;

	/**
	 * Create a PreparedStatementSpy (JDBC 4 version) for logging activity of another PreparedStatement.
	 *
	 * @param sql                   SQL for the prepared statement that is being spied upon.
	 * @param connectionSpy         ConnectionSpy that was called to produce this PreparedStatement.
	 * @param realPreparedStatement The actual PreparedStatement that is being spied upon.
	 * @throws SQLException 
	 * @throws ParseException 
	 */
	public PreparedStatementSpy(String sql, ConnectionSpy connectionSpy) throws SQLException
	{

		super(connectionSpy);  //
		ArrayList<String> sqls;
		try {
			sqls = rewriteSql(sql);
		} catch (ParseException e) {
			throw new SQLException("Cannot temporally-rewrite correctly:" +sql);
		}
		realSqls = new ArrayList<PreparedStatement>();
		for(String s:sqls){
			realSqls.add(connectionSpy.realConnection.prepareStatement(s));
		}
		this.sql = sql;
		this.sqls= sqls;
		rdbmsSpecifics = connectionSpy.getRdbmsSpecifics();
	}
	public void close() throws SQLException
	{
	    String methodCall = "close()";
	    try
	    {
			for(PreparedStatement realPreparedStatement:realSqls){		
				realPreparedStatement.close();
			}
	    }
	    catch (SQLException s)
	    {
	      reportException(methodCall, s);
	      throw s;
	    }
	    reportReturn(methodCall);
	}
	public void clearBatch() throws SQLException
	{
	    String methodCall = "clearBatch()";
	    try
	    {
			for(PreparedStatement realPreparedStatement:realSqls)		
				realPreparedStatement.clearBatch();
	    }
	    catch (SQLException s)
	    {
	      reportException(methodCall, s);
	      throw s;
	    }
	    currentBatch.clear();
	    reportReturn(methodCall);
	}
	public PreparedStatementSpy(String sql, ConnectionSpy connectionSpy,int autoGeneratedKeys) throws SQLException
	{

		super(connectionSpy);  //
		ArrayList<String> sqls;
		try {
			sqls = rewriteSql(sql);
		} catch (ParseException e) {
			throw new SQLException("Cannot temporally-rewrite correctly:" +sql);
		}
		realSqls = new ArrayList<PreparedStatement>();
		for(String s:sqls){
			realSqls.add(connectionSpy.realConnection.prepareStatement(s,autoGeneratedKeys));
		}
		numberOfNonTemporalParameters=-1;
		this.sql = sql;
		rdbmsSpecifics = connectionSpy.getRdbmsSpecifics();
	}




	public PreparedStatementSpy(String sql, ConnectionSpy connectionSpy,int resultSetType, int resultSetConcurrency) throws SQLException
	{

		super(connectionSpy);  //
		ArrayList<String> sqls;
		try {
			sqls = rewriteSql(sql);
		} catch (ParseException e) {
			throw new SQLException("Cannot temporally-rewrite correctly:" +sql);
		}
		realSqls = new ArrayList<PreparedStatement>();
		for(String s:sqls){
			realSqls.add(connectionSpy.realConnection.prepareStatement(s,resultSetType,resultSetConcurrency));
		}
		numberOfNonTemporalParameters=-1;
		this.sql = sql;
		rdbmsSpecifics = connectionSpy.getRdbmsSpecifics();
	}


	public PreparedStatementSpy(String sql, ConnectionSpy connectionSpy,int resultSetType, int resultSetConcurrency,
			int resultSetHoldability) throws SQLException
			{

		super(connectionSpy);  //
		ArrayList<String> sqls;
		try {
			sqls = rewriteSql(sql);
		} catch (ParseException e) {
			throw new SQLException("Cannot temporally-rewrite correctly:" +sql);
		}
		realSqls = new ArrayList<PreparedStatement>();
		for(String s:sqls){
			realSqls.add(connectionSpy.realConnection.prepareStatement(s,resultSetType,resultSetConcurrency,
					resultSetHoldability));
		}
		numberOfNonTemporalParameters=-1;
		this.sql = sql;
		rdbmsSpecifics = connectionSpy.getRdbmsSpecifics();
			}

	public PreparedStatementSpy(String sql, ConnectionSpy connectionSpy,int columnIndexes[]) throws SQLException
	{

		super(connectionSpy);  //
		ArrayList<String> sqls;
		try {
			sqls = rewriteSql(sql);
		} catch (ParseException e) {
			throw new SQLException("Cannot temporally-rewrite correctly:" +sql);
		}
		realSqls = new ArrayList<PreparedStatement>();
		for(String s:sqls){
			realSqls.add(connectionSpy.realConnection.prepareStatement(s, columnIndexes));
		}
		numberOfNonTemporalParameters=-1;
		this.sql = sql;
		rdbmsSpecifics = connectionSpy.getRdbmsSpecifics();
	}

	public PreparedStatementSpy(String sql, ConnectionSpy connectionSpy,String columnNames[]) throws SQLException
	{

		super(connectionSpy);  //
		ArrayList<String> sqls;
		try {
			sqls = rewriteSql(sql);
		} catch (ParseException e) {
			throw new SQLException("Cannot temporally-rewrite correctly:" +sql);
		}
		realSqls = new ArrayList<PreparedStatement>();
		for(String s:sqls){
			realSqls.add(connectionSpy.realConnection.prepareStatement(s,columnNames));
		}
		numberOfNonTemporalParameters=-1;
		this.sql = sql;
		rdbmsSpecifics = connectionSpy.getRdbmsSpecifics();
	}



	public String getClassType()
	{
		return "PreparedStatement";
	}

	// forwarding methods

	public void setTime(int parameterIndex, Time x) throws SQLException
	{
		String methodCall = "setTime(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Time)", x);
		try
		{

			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setTime(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setTime(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException
	{
		String methodCall = "setTime(" + parameterIndex + ", " + x + ", " + cal + ")";
		argTraceSet(parameterIndex, "(Time)", x);
		try
		{

			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setTime(parameterIndex-numberOfNonTemporalParameters, x, cal);
				first=false;
				}else{
					realPreparedStatement.setTime(parameterIndex, x, cal);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException
	{
		String methodCall = "setCharacterStream(" + parameterIndex + ", " + reader + ", " + length + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
		try
		{

			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setCharacterStream(parameterIndex-numberOfNonTemporalParameters, reader, length);
				first=false;
				}else{
					realPreparedStatement.setCharacterStream(parameterIndex, reader, length);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNull(int parameterIndex, int sqlType) throws SQLException
	{
		String methodCall = "setNull(" + parameterIndex + ", " + sqlType + ")";
		argTraceSet(parameterIndex, null, null);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNull(parameterIndex-numberOfNonTemporalParameters, sqlType);
				first=false;
				}else{
					realPreparedStatement.setNull(parameterIndex, sqlType);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException
	{
		String methodCall = "setNull(" + parameterIndex + ", " + sqlType + ", " + typeName + ")";
		argTraceSet(parameterIndex, null, null);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNull(parameterIndex-numberOfNonTemporalParameters,  sqlType, typeName);
				first=false;
				}else{
					realPreparedStatement.setNull(parameterIndex,  sqlType, typeName);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setRef(int parameterIndex, Ref x) throws SQLException
	{
		String methodCall = "setRef(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Ref)", x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setRef(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setRef(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBoolean(int parameterIndex, boolean x) throws SQLException
	{
		String methodCall = "setBoolean(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(boolean)", x?Boolean.TRUE:Boolean.FALSE);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBoolean(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setBoolean(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBlob(int parameterIndex, Blob x) throws SQLException
	{
		String methodCall = "setBlob(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Blob)", 
				x==null?null:("<Blob of size " + x.length() + ">"));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBlob(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setBlob(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setClob(int parameterIndex, Clob x) throws SQLException
	{
		String methodCall = "setClob(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Clob)",
				x==null?null:("<Clob of size " + x.length() + ">"));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setClob(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setClob(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setArray(int parameterIndex, Array x) throws SQLException
	{
		String methodCall = "setArray(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Array)", "<Array>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setArray(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setArray(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setByte(int parameterIndex, byte x) throws SQLException
	{
		String methodCall = "setByte(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(byte)", new Byte(x));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setByte(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setByte(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	/**
	 * @deprecated
	 */
	public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException
	{
		String methodCall = "setUnicodeStream(" + parameterIndex + ", " + x + ", " + length + ")";
		argTraceSet(parameterIndex, "(Unicode InputStream)", "<Unicode InputStream of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setUnicodeStream(parameterIndex-numberOfNonTemporalParameters, x, length);
				first=false;
				}else{
					realPreparedStatement.setUnicodeStream(parameterIndex, x, length);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setShort(int parameterIndex, short x) throws SQLException
	{
		String methodCall = "setShort(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(short)", new Short(x));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setShort(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setShort(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public boolean execute() throws SQLException
	{
		String methodCall = "execute()";
		String dumpedSql = dumpedSql();
		reportSql(dumpedSql, methodCall);
		long tstart = System.currentTimeMillis();
		try
		{
			boolean result=false;
			for(PreparedStatement realPreparedStatement:realSqls){		
				result = realPreparedStatement.execute();
			}
			reportSqlTiming(System.currentTimeMillis() - tstart, dumpedSql, methodCall);
			return reportReturn(methodCall, result);
		}
		catch (SQLException s)
		{
			reportException(methodCall, s, dumpedSql, System.currentTimeMillis() - tstart);
			throw s;
		}
	}

	public void setInt(int parameterIndex, int x) throws SQLException
	{
		String methodCall = "setInt(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(int)", new Integer(x));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls)
			{		
				if(realSqls.size()>1 && first)
				{        	
					if(parameterIndex-numberOfNonTemporalParameters>0)
						realPreparedStatement.setInt(parameterIndex-numberOfNonTemporalParameters, x);
					first=false;
				}
				else
				{
					realPreparedStatement.setInt(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setLong(int parameterIndex, long x) throws SQLException
	{
		String methodCall = "setLong(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(long)", new Long(x));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setLong(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setLong(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setFloat(int parameterIndex, float x) throws SQLException
	{
		String methodCall = "setFloat(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(float)", new Float(x));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setFloat(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setFloat(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setDouble(int parameterIndex, double x) throws SQLException
	{
		String methodCall = "setDouble(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(double)", new Double(x));
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setDouble(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setDouble(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException
	{
		String methodCall = "setBigDecimal(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(BigDecimal)", x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBigDecimal(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setBigDecimal(parameterIndex, x);
				}
			}


		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setURL(int parameterIndex, URL x) throws SQLException
	{
		String methodCall = "setURL(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(URL)", x);

		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setURL(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setURL(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setString(int parameterIndex, String x) throws SQLException
	{
		String methodCall = "setString(" + parameterIndex + ", \"" + x + "\")";
		argTraceSet(parameterIndex, "(String)", x);

		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first)
				{        		  
					if(parameterIndex-numberOfNonTemporalParameters>0)
						realPreparedStatement.setString(parameterIndex-numberOfNonTemporalParameters, x);
					first=false;
				}
				else
				{
					realPreparedStatement.setString(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBytes(int parameterIndex, byte[] x) throws SQLException
	{
		//todo: dump array?
		String methodCall = "setBytes(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(byte[])", "<byte[]>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBytes(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setBytes(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setDate(int parameterIndex, Date x) throws SQLException
	{
		String methodCall = "setDate(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Date)", x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setDate(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setDate(parameterIndex, x);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public ParameterMetaData getParameterMetaData() throws SQLException
	{
		String methodCall = "getParameterMetaData()";
		try
		{

			//FIXME not sure about this... it's an attempt..
			return (ParameterMetaData) reportReturn(methodCall, realSqls.get(0).getParameterMetaData());
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
	}

	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		String methodCall = "setRowId(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(RowId)", x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setRowId(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setRowId(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNString(int parameterIndex, String value) throws SQLException {
		String methodCall = "setNString(" + parameterIndex + ", " + value + ")";
		argTraceSet(parameterIndex, "(String)", value);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNString(parameterIndex-numberOfNonTemporalParameters, value);
				first=false;
				}else{
					realPreparedStatement.setNString(parameterIndex, value);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
		String methodCall = "setNCharacterStream(" + parameterIndex + ", " + value + ", " + length + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNCharacterStream(parameterIndex-numberOfNonTemporalParameters,value, length);
				first=false;
				}else{
					realPreparedStatement.setNCharacterStream(parameterIndex, value, length);
				}
			}


		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		String methodCall = "setNClob(" + parameterIndex + ", " + value + ")";
		argTraceSet(parameterIndex, "(NClob)", "<NClob>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNClob(parameterIndex-numberOfNonTemporalParameters, value);
				first=false;
				}else{
					realPreparedStatement.setNClob(parameterIndex, value);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
		String methodCall = "setClob(" + parameterIndex + ", " + reader + ", " + length + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setClob(parameterIndex-numberOfNonTemporalParameters, reader, length);
				first=false;
				}else{
					realPreparedStatement.setClob(parameterIndex, reader, length);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
		String methodCall = "setBlob(" + parameterIndex + ", " + inputStream + ", " + length + ")";
		argTraceSet(parameterIndex, "(InputStream)", "<InputStream of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBlob(parameterIndex-numberOfNonTemporalParameters, inputStream, length);
				first=false;
				}else{
					realPreparedStatement.setBlob(parameterIndex,  inputStream, length);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
		String methodCall = "setNClob(" + parameterIndex + ", " + reader + ", " + length + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNClob(parameterIndex-numberOfNonTemporalParameters,  reader, length);
				first=false;
				}else{
					realPreparedStatement.setNClob(parameterIndex, reader, length);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
		String methodCall = "setSQLXML(" + parameterIndex + ", " + xmlObject + ")";
		argTraceSet(parameterIndex, "(SQLXML)", xmlObject);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setSQLXML(parameterIndex-numberOfNonTemporalParameters, xmlObject);
				first=false;
				}else{
					realPreparedStatement.setSQLXML(parameterIndex, xmlObject);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException
	{
		String methodCall = "setDate(" + parameterIndex + ", " + x + ", " + cal + ")";
		argTraceSet(parameterIndex, "(Date)", x);

		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setDate(parameterIndex-numberOfNonTemporalParameters, x,cal);
				first=false;
				}else{
					realPreparedStatement.setDate(parameterIndex, x,cal);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public ResultSet executeQuery() throws SQLException
	{
		String methodCall = "executeQuery()";
		String dumpedSql = dumpedSql();
		reportSql(dumpedSql, methodCall);
		long tstart = System.currentTimeMillis();
		try
		{
			reportSql("Translate : "+sqls.toString(), methodCall);
			//System.out.println("Translate: "+ sqls.toString());
			ResultSet r = realSqls.get(0).executeQuery();
			reportSqlTiming(System.currentTimeMillis() - tstart, dumpedSql, methodCall);
			ResultSetSpy rsp = new ResultSetSpy(this, r);
			return (ResultSet) reportReturn(methodCall, rsp);
		}
		catch (SQLException s)
		{
			reportException(methodCall, s, dumpedSql, System.currentTimeMillis() - tstart);
			throw s;
		}
	}

	private String getTypeHelp(Object x)
	{
		if (x==null)
		{
			return "(null)";
		}
		else
		{
			return "(" + x.getClass().getName() + ")";
		}
	}

	public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws SQLException
	{
		String methodCall = "setObject(" + parameterIndex + ", " + x + ", " + targetSqlType + ", " + scale + ")";
		argTraceSet(parameterIndex, getTypeHelp(x), x);

		try
		{

			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setObject(parameterIndex-numberOfNonTemporalParameters, x, targetSqlType, scale);
				first=false;
				}else{
					realPreparedStatement.setObject(parameterIndex, x, targetSqlType, scale);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	/**
	 * Sets the designated parameter to the given input stream, which will have
	 * the specified number of bytes.
	 * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
	 * parameter, it may be more practical to send it via a
	 * <code>java.io.InputStream</code>. Data will be read from the stream
	 * as needed until end-of-file is reached.  The JDBC driver will
	 * do any necessary conversion from ASCII to the database char format.
	 * <p/>
	 * <P><B>Note:</B> This stream object can either be a standard
	 * Java stream object or your own subclass that implements the
	 * standard interface.
	 *
	 * @param parameterIndex the first parameter is 1, the second is 2, ...
	 * @param x              the Java input stream that contains the ASCII parameter value
	 * @param length         the number of bytes in the stream
	 * @throws java.sql.SQLException if parameterIndex does not correspond to a parameter
	 *                               marker in the SQL statement; if a database access error occurs or
	 *                               this method is called on a closed <code>PreparedStatement</code>
	 * @since 1.6
	 */
	public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
		String methodCall = "setAsciiStream(" + parameterIndex + ", " + x + ", " + length + ")";
		argTraceSet(parameterIndex, "(Ascii InputStream)", "<Ascii InputStream of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setAsciiStream(parameterIndex-numberOfNonTemporalParameters, x, length);
				first=false;
				}else{
					realPreparedStatement.setAsciiStream(parameterIndex, x, length);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
		String methodCall = "setBinaryStream(" + parameterIndex + ", " + x + ", " + length + ")";
		argTraceSet(parameterIndex, "(Binary InputStream)", "<Binary InputStream of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBinaryStream(parameterIndex-numberOfNonTemporalParameters, x,length);
				first=false;
				}else{
					realPreparedStatement.setBinaryStream(parameterIndex, x,length);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
		String methodCall = "setCharacterStream(" + parameterIndex + ", " + reader + ", " + length + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setCharacterStream(parameterIndex-numberOfNonTemporalParameters,reader, length);
				first=false;
				}else{
					realPreparedStatement.setCharacterStream(parameterIndex, reader, length);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);

	}

	public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
		String methodCall = "setAsciiStream(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Ascii InputStream)", "<Ascii InputStream>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setAsciiStream(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setAsciiStream(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
		String methodCall = "setBinaryStream(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Binary InputStream)", "<Binary InputStream>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBinaryStream(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setBinaryStream(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);

	}

	public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
		String methodCall = "setCharacterStream(" + parameterIndex + ", " + reader + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setCharacterStream(parameterIndex-numberOfNonTemporalParameters, reader);
				first=false;
				}else{
					realPreparedStatement.setCharacterStream(parameterIndex, reader);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNCharacterStream(int parameterIndex, Reader reader) throws SQLException {
		String methodCall = "setNCharacterStream(" + parameterIndex + ", " + reader + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNCharacterStream(parameterIndex-numberOfNonTemporalParameters, reader);
				first=false;
				}else{
					realPreparedStatement.setNCharacterStream(parameterIndex, reader);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setClob(int parameterIndex, Reader reader) throws SQLException {
		String methodCall = "setClob(" + parameterIndex + ", " + reader + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setClob(parameterIndex-numberOfNonTemporalParameters, reader);
				first=false;
				}else{
					realPreparedStatement.setClob(parameterIndex, reader);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
		String methodCall = "setBlob(" + parameterIndex + ", " + inputStream + ")";
		argTraceSet(parameterIndex, "(InputStream)", "<InputStream>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBlob(parameterIndex-numberOfNonTemporalParameters, inputStream);
				first=false;
				}else{
					realPreparedStatement.setBlob(parameterIndex, inputStream);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		String methodCall = "setNClob(" + parameterIndex + ", " + reader + ")";
		argTraceSet(parameterIndex, "(Reader)", "<Reader>");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setNClob(parameterIndex-numberOfNonTemporalParameters, reader);
				first=false;
				}else{
					realPreparedStatement.setNClob(parameterIndex, reader);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);

	}

	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException
	{
		String methodCall = "setObject(" + parameterIndex + ", " + x + ", " + targetSqlType + ")";
		argTraceSet(parameterIndex, getTypeHelp(x), x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setObject(parameterIndex-numberOfNonTemporalParameters, x, targetSqlType);
				first=false;
				}else{
					realPreparedStatement.setObject(parameterIndex, x, targetSqlType);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setObject(int parameterIndex, Object x) throws SQLException
	{
		String methodCall = "setObject(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, getTypeHelp(x), x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setObject(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setObject(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException
	{
		String methodCall = "setTimestamp(" + parameterIndex + ", " + x + ")";
		argTraceSet(parameterIndex, "(Date)", x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setTimestamp(parameterIndex-numberOfNonTemporalParameters, x);
				first=false;
				}else{
					realPreparedStatement.setTimestamp(parameterIndex, x);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException
	{
		String methodCall = "setTimestamp(" + parameterIndex + ", " + x + ", " + cal + ")";
		argTraceSet(parameterIndex, "(Timestamp)", x);
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setTimestamp(parameterIndex-numberOfNonTemporalParameters, x,cal);
				first=false;
				}else{
					realPreparedStatement.setTimestamp(parameterIndex, x,cal);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public int executeUpdate() throws SQLException
	{
		String methodCall = "executeUpdate()";
		String dumpedSql = dumpedSql();
		reportSql(dumpedSql, methodCall);
		long tstart = System.currentTimeMillis();
		try
		{
			int result=-1;
			reportSql("Translate: "+sqls.toString(), methodCall);
			//System.out.println("Translate: "+sqls.toString());
			for(PreparedStatement realPreparedStatement:realSqls)
			{	
				//System.out.println(realPreparedStatement);
				result = realPreparedStatement.executeUpdate();
			}
			reportSqlTiming(System.currentTimeMillis() - tstart, dumpedSql, methodCall);
			int ret=reportReturn(methodCall, result);
			//System.out.println("return value:" + ret);
			return ret;
		}
		catch (SQLException s)
		{
			//System.out.println(realSqls);
			reportException(methodCall, s, dumpedSql, System.currentTimeMillis() - tstart);
			throw s;
		}
	}

	public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException
	{
		String methodCall = "setAsciiStream(" + parameterIndex + ", " + x + ", " + length + ")";
		argTraceSet(parameterIndex, "(Ascii InputStream)", "<Ascii InputStream of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setAsciiStream(parameterIndex-numberOfNonTemporalParameters, x,length);
				first=false;
				}else{
					realPreparedStatement.setAsciiStream(parameterIndex, x,length);
				}
			}

		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException
	{
		String methodCall = "setBinaryStream(" + parameterIndex + ", " + x + ", " + length + ")";
		argTraceSet(parameterIndex, "(Binary InputStream)", "<Binary InputStream of length " + length + ">");
		try
		{
			boolean first = true;	
			for(PreparedStatement realPreparedStatement:realSqls){		
				if(realSqls.size()>1 && first){        		  if(parameterIndex-numberOfNonTemporalParameters>0)
					realPreparedStatement.setBinaryStream(parameterIndex-numberOfNonTemporalParameters, x,length);
				first=false;
				}else{
					realPreparedStatement.setBinaryStream(parameterIndex, x,length);
				}
			}
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public void clearParameters() throws SQLException
	{
		String methodCall = "clearParameters()";

		synchronized (argTrace)
		{
			argTrace.clear();
		}

		try
		{
			for(PreparedStatement realPreparedStatement:realSqls){	

				realPreparedStatement.clearParameters();
			}


		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public ResultSetMetaData getMetaData() throws SQLException
	{
		String methodCall = "getMetaData()";
		try
		{
			//FIXME check this..
			return (ResultSetMetaData) reportReturn(methodCall, realSqls.get(0).getMetaData());
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
	}

	/**
	 * Warning: This can't be right, as Update+Insert operation needs to be successive
	 * Can't run a batch of Updates then a batch of Inserts.
	 */
	public void addBatch() throws SQLException
	{
		String methodCall = "addBatch()";
		currentBatch.add(dumpedSql());
		try
		{
			//FIXME check this
	    	for(PreparedStatement realStatement:realSqls)
	    		realStatement.addBatch();
		}
		catch (SQLException s)
		{
			reportException(methodCall, s);
			throw s;
		}
		reportReturn(methodCall);
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		String methodCall = "unwrap(" + (iface==null?"null":iface.getName()) + ")";
		try
		{
			//todo: double check this logic
			//NOTE: could call super.isWrapperFor to simplify this logic, but it would result in extra log output
			//because the super classes would be invoked, thus executing their logging methods too...
			return (T)reportReturn(methodCall,
					(iface != null && (iface==PreparedStatement.class||iface==Statement.class||iface==Spy.class))?
							(T)this:
								//FIXME check this
								realSqls.get(0).unwrap(iface));
		}
		catch (SQLException s)
		{
			reportException(methodCall,s);
			throw s;
		}
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException
	{
		String methodCall = "isWrapperFor(" + (iface==null?"null":iface.getName()) + ")";
		try
		{
			//NOTE: could call super.isWrapperFor to simplify this logic, but it would result in extra log output
			//when the super classes would be invoked..
			return reportReturn(methodCall,
					(iface != null && (iface==PreparedStatement.class||iface==Statement.class||iface==Spy.class)) ||
					//FIXME check this	
					realSqls.get(0).isWrapperFor(iface));
		}
		catch (SQLException s)
		{
			reportException(methodCall,s);
			throw s;
		}
	}


	/**
	 * temporal rewriting 
	 * @param sql
	 * @return
	 * @throws ParseException 
	 */
	private ArrayList<String> rewriteSql(String sql) throws ParseException {		
		//System.out.println(sql);
		Parser p = new Parser(connectionSpy.schema.getSchemaName(),connectionSpy.schema, sql);
		ArrayList<String> li = new ArrayList<String>();
		if(p.isTemporal())
			li.add(p.toNonTemporalSQL());
		else
			li = p.rewriteToTemporal();

		numberOfNonTemporalParameters = p.getNonTemporalParametersCount();

		//System.out.println("translation:");
		//for(String s:li)
			//System.out.println(s);
		return li;
	}
	/**
	 * 
	 */
	public String toString()
	{
		return getRealPreparedStatements().toString();
	}
}
