/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package us.curino.temporalsql;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;




public class TestJDBCProxy {

  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {

    Properties ini = new Properties();
    try 
    {
    	System.out.println(args[0]);
    	ini.load(new FileInputStream(args[0]));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    String path= ini.getProperty("conn") + ini.getProperty("schema");
    String schema= ini.getProperty("schema");
    String user= ini.getProperty("user");
    String password= ini.getProperty("password");
    
    // Register jdbcDriver
    try {
      Class.forName(ini.getProperty("driver"));
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    // wrap the connection with log4jdbc    
    Connection conn= TemporalDriverManager.getConnection(path, user, password, schema);
    
    Statement st = conn.createStatement();
	
    ResultSet res= st.executeQuery("SELECT * FROM warehouse WHERE w_id=7;");
        
    while(res.next()){  	 
    	for(int i=1;i<=res.getMetaData().getColumnCount();i++)
    		System.out.print(res.getString(i) + " ");
    	System.out.println("\n");
    }
    st.execute("Update warehouse set w_id=2");
    
    PreparedStatement ps = conn.prepareStatement("SELECT * FROM warehouse WHERE w_id=?");
    ps.setInt(1, 7);
    res = ps.executeQuery();
    while(res.next())
    {  	 
    	for(int i=1;i<=res.getMetaData().getColumnCount();i++)
    		System.out.print(res.getString(i) + " ");
    	System.out.println("\n");
    }

    ps = conn.prepareStatement("INSERT INTO  warehouse (w_id) VALUES (?)");
    ps.setInt(1, 12);
    int t = ps.executeUpdate();
    System.out.println(t + " rows affected");
    
    Thread.sleep(4000);
    
    ps = conn.prepareStatement("UPDATE warehouse SET w_state=? WHERE w_id=?");
    ps.setString(1,"MA");
    ps.setInt(2, 13);
    t = ps.executeUpdate();
    System.out.println(t + " rows affected");

    Thread.sleep(4000);

    
    ps = conn.prepareStatement("DELETE FROM warehouse WHERE w_id=?");
    ps.setInt(1, 13);
    t = ps.executeUpdate();
    System.out.println(t + " rows affected");
    
    ps = conn.prepareStatement("SELECT id,w_state,tstart,tend FROM warehouse VERSIONS BEFORE SYSTEM TIME 2037-12-31 23:59:58;");
    ResultSet rs = ps.executeQuery();
    while(rs.next()){
    	
    	for(int i=1;i<=4;i++)
    		System.out.print(rs.getString(i) + " ");
    	System.out.print("\n");
    	
    }
    
    /*while(res.next()){
    	System.out.println("PROCESSING:" + res.getString(1));
    	st2.executeUpdate("ALTER TABLE "+res.getString(1)+ " ADD COLUMN tstart TIMESTAMP NOT NULL;");
    	st2.executeUpdate("ALTER TABLE "+res.getString(1)+ " ADD COLUMN tend TIMESTAMP NOT NULL DEFAULT '2037-12-31 23:59:59';");
    	
    }*/
  }  
      

}
