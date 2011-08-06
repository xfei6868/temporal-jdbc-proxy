import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

import com.relationalcloud.tsqlparser.Parser;
import com.relationalcloud.tsqlparser.loader.Schema;
import com.relationalcloud.tsqlparser.loader.SchemaLoader;


public class test {

  public static void main(String[] args) {

    // Register jdbcDriver
    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  
    Connection conn1 = null;
    //Connection conn = null;
    try 
    {
    
      conn1 = DriverManager.getConnection("jdbc:mysql://localhost:3306/information_schema", "root", "hello");
      System.out.println("Loading the schema ..");
      Schema s = SchemaLoader.loadSchemaFromDB(conn1,"tpcc");
      
      //conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/tpcc", "root", "hello");
      
      String sql= "INSERT INTO history (h_c_d_id, h_c_w_id, h_c_id, h_d_id, h_w_id, h_date, h_amount, h_data) "
                            + " VALUES (?,?,?,?,?,?,?,?)";
      
      Parser p = new Parser("tpcc",s, sql);
      System.out.println(p.rewriteToTemporal());
    }catch(Exception e){
      
      e.printStackTrace();
      
    }
  }
  
  
}
