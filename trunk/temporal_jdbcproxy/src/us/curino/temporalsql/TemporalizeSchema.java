package us.curino.temporalsql;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class TemporalizeSchema {

  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {

    Properties ini = new Properties();
    try {
    	System.out.println(args[0]);
      ini.load(new FileInputStream(args[0]));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Register jdbcDriver
    try {
      Class.forName(ini.getProperty("driver"));
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    String schema=ini.getProperty("schema");
    Connection conn = DriverManager.getConnection(ini.getProperty("conn") + schema, ini.getProperty("user"), ini.getProperty("password"));
 
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
	
    ResultSet res= st.executeQuery("SELECT table_name FROM information_schema.TABLES WHERE information_schema.TABLES.TABLE_SCHEMA='" + ini.getProperty("schema")+"';");
    
    //ResultSet res= st.executeQuery("SET @a = NOW();");
    //res= st.executeQuery("SELECT @a;");
   	while (res.next())
   	{
   		String table=res.getString(1);
   		System.out.println("Temporizing table: " + table);
	    Statement st3=conn.createStatement();
	    //System.out.println("SELECT k.COLUMN_NAME FROM information_schema.table_constraints t LEFT JOIN information_schema.key_column_usage k USING(constraint_name,table_schema,table_name) " +
			//	   "WHERE t.constraint_type='PRIMARY KEY'     AND t.table_schema='"+schema+"'  AND t.table_name='"+table+"'");
	    ResultSet resprim=st3.executeQuery("SELECT k.COLUMN_NAME FROM information_schema.table_constraints t LEFT JOIN information_schema.key_column_usage k USING(constraint_name,table_schema,table_name) " +
	    								   "WHERE t.constraint_type='PRIMARY KEY'     AND t.table_schema='"+schema+"'  AND t.table_name='"+table+"'");
	    String pk="";
	    while(resprim.next())
	    {
	    	pk=pk+resprim.getString(1)+",";
	    }
   		/* MySQL */
   		st2.executeUpdate("ALTER TABLE "+table+ " ADD COLUMN tstart DECIMAL(20,6) NOT NULL DEFAULT 0;");
   		st2.executeUpdate("ALTER TABLE "+table+ " ADD COLUMN tend DECIMAL(20,6) NOT NULL DEFAULT '20371231235959.000000';");
   		/* PK */
   		if(pk.length()>0)
   		{
   			System.out.println("Alter table "+table+" drop primary key");
   			st2.executeUpdate("Alter table "+table+" drop primary key");
   	   		System.out.println("Alter table "+table+" add primary key("+pk+"tend)");
   	   		st2.executeUpdate("Alter table "+table+" add primary key("+pk+"tend)");
   		}
   		st3.close();
	   	/* PostGreSQL */
	   	//st2.executeUpdate("ALTER TABLE "+table+ " ADD COLUMN tstart TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;");
	   	//st2.executeUpdate("ALTER TABLE "+table+ " ADD COLUMN tend TIMESTAMP NOT NULL DEFAULT '2037-12-31 23:59:59';");
   	}
   	st.close();
   	st2.close();
   	conn.close();
  }  
      

}
